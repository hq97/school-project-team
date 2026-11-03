package com.school.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.asset.entity.SpaceOccupancy;
import com.school.asset.mapper.SpaceOccupancyMapper;
import com.school.common.util.Ids;
import com.school.schedule.entity.ScheduleChange;
import com.school.schedule.entity.TimetableEntry;
import com.school.schedule.mapper.ScheduleChangeMapper;
import com.school.schedule.mapper.TimetableEntryMapper;
import com.school.workflow.service.ApprovalResultHandler;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 调课/代课审批结果回写：终审通过 → 联动课表 + 场地占用 + 消息；驳回/终止 → REJECTED + 通知。
 *
 * <p>回调在 {@code FlowEngineService#approve} 同一事务内执行（REQUIRED 传播），业务状态与流程状态原子提交/回滚。
 * 只做按 businessId 的定点写回（selectById/updateById/insert/delete），满足门禁4 规则③「单点写回豁免」口径。</p>
 */
@Component
public class ScheduleChangeApprovalHandler implements ApprovalResultHandler {

    private final ScheduleChangeMapper changeMapper;
    private final TimetableEntryMapper entryMapper;
    private final SpaceOccupancyMapper occupancyMapper;
    private final ScheduleConflictChecker conflictChecker;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    public ScheduleChangeApprovalHandler(ScheduleChangeMapper changeMapper, TimetableEntryMapper entryMapper,
                                         SpaceOccupancyMapper occupancyMapper, ScheduleConflictChecker conflictChecker,
                                         MessageService messageService, ObjectMapper objectMapper) {
        this.changeMapper = changeMapper;
        this.entryMapper = entryMapper;
        this.occupancyMapper = occupancyMapper;
        this.conflictChecker = conflictChecker;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String businessType() {
        return "schedule_change";
    }

    @Override
    public void onApproved(String businessId) {
        ScheduleChange change = changeMapper.selectById(businessId);
        if (change == null || !"PENDING".equals(change.getStatus())) {
            return;
        }
        TimetableEntry entry = entryMapper.selectById(change.getEntryId());
        if (entry == null) {
            reject(change, "课表条目不存在，已自动驳回");
            return;
        }

        if ("CHANGE".equals(change.getType())) {
            Map<String, Object> slot = parseSlot(change.getTargetSlot());
            int targetDay = asInt(slot.get("dayOfWeek"));
            int targetPeriod = asInt(slot.get("period"));
            // AC-H3-2：终审前二次校验目标时段仍无冲突，冲突则回退 REJECTED
            String conflict = conflictChecker.check(entry.getTermId(), entry.getWeek(), targetDay, targetPeriod,
                    entry.getTeacherId(), entry.getClassId(), entry.getSpaceId(), entry.getId());
            if (conflict != null) {
                reject(change, "目标时段冲突（" + conflict + "），审批通过后自动回退");
                return;
            }
            int oldDay = entry.getDayOfWeek();
            int oldPeriod = entry.getPeriod();
            entry.setDayOfWeek(targetDay);
            entry.setPeriod(targetPeriod);
            entryMapper.updateById(entry);
            syncOccupancy(entry, oldDay, oldPeriod, entry.getDayOfWeek(), entry.getPeriod());
        } else {
            // SUBSTITUTE / LEAVE_SUBSTITUTE：原地代课，标注 substituteTeacherId
            entry.setSubstituteTeacherId(change.getSubstituteTeacherId());
            entryMapper.updateById(entry);
        }

        change.setStatus("APPROVED");
        changeMapper.updateById(change);

        messageService.notify(change.getApplicantId(), "SCHEDULE",
                "调课/代课申请已通过（" + change.getApplyNo() + "）", "schedule_change", change.getId());
        if (change.getSubstituteTeacherId() != null && !change.getSubstituteTeacherId().isBlank()
                && !change.getSubstituteTeacherId().equals(change.getApplicantId())) {
            messageService.notify(change.getSubstituteTeacherId(), "SCHEDULE",
                    "您被安排代课（" + change.getApplyNo() + "）", "schedule_change", change.getId());
        }
    }

    @Override
    public void onRejected(String businessId) {
        ScheduleChange change = changeMapper.selectById(businessId);
        if (change == null || !"PENDING".equals(change.getStatus())) {
            return;
        }
        change.setStatus("REJECTED");
        changeMapper.updateById(change);
        messageService.notify(change.getApplicantId(), "SCHEDULE",
                "调课/代课申请已驳回（" + change.getApplyNo() + "）", "schedule_change", change.getId());
    }

    private void reject(ScheduleChange change, String reason) {
        change.setStatus("REJECTED");
        change.setReason((change.getReason() == null ? "" : change.getReason() + "；") + reason);
        changeMapper.updateById(change);
        messageService.notify(change.getApplicantId(), "SCHEDULE",
                "调课/代课申请未通过（" + change.getApplyNo() + "）：" + reason, "schedule_change", change.getId());
    }

    /** 调课换时段：释放原时段 SCHEDULE 占用，写入新时段占用（bizRefId=entryId）。 */
    private void syncOccupancy(TimetableEntry entry, int oldDay, int oldPeriod, int newDay, int newPeriod) {
        occupancyMapper.delete(new QueryWrapper<SpaceOccupancy>()
                .eq("\"spaceId\"", entry.getSpaceId())
                .eq("\"termId\"", entry.getTermId())
                .eq("\"week\"", entry.getWeek())
                .eq("\"dayOfWeek\"", oldDay)
                .eq("\"period\"", oldPeriod)
                .eq("\"source\"", "SCHEDULE")
                .eq("\"bizRefId\"", entry.getId()));

        SpaceOccupancy occ = new SpaceOccupancy();
        occ.setId(Ids.next("occ"));
        occ.setSpaceId(entry.getSpaceId());
        occ.setTermId(entry.getTermId());
        occ.setWeek(entry.getWeek());
        occ.setDayOfWeek(newDay);
        occ.setPeriod(newPeriod);
        occ.setSource("SCHEDULE");
        occ.setBizRefId(entry.getId());
        occ.setStatus("EFFECTIVE");
        occ.setCreatedBy(entry.getTeacherId());
        occ.setCreatedAt(OffsetDateTime.now());
        occupancyMapper.insert(occ);
    }

    private Map<String, Object> parseSlot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("目标时段解析失败", e);
        }
    }

    private int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o));
    }
}
