package com.school.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.util.Ids;
import com.school.schedule.dto.ScheduleChangeApplyRequest;
import com.school.schedule.entity.ScheduleChange;
import com.school.schedule.entity.TimetableEntry;
import com.school.schedule.mapper.ScheduleChangeMapper;
import com.school.schedule.mapper.TimetableEntryMapper;
import com.school.workflow.service.FlowEngineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调课/代课/请假代课申请（M08）。docs/11 REQ-S4/S5。
 *
 * <p>申请归属 SELF（防越权改他人课）；审批结果由 {@link ScheduleChangeApprovalHandler} 在
 * FlowEngine 事务内联动课表/场地/消息。</p>
 */
@Service
public class ScheduleChangeService {

    private static final List<String> TYPES = List.of("CHANGE", "SUBSTITUTE", "LEAVE_SUBSTITUTE");

    private final ScheduleChangeMapper changeMapper;
    private final TimetableEntryMapper entryMapper;
    private final ScheduleConflictChecker conflictChecker;
    private final FlowEngineService flowEngine;
    private final ObjectMapper objectMapper;

    public ScheduleChangeService(ScheduleChangeMapper changeMapper, TimetableEntryMapper entryMapper,
                                 ScheduleConflictChecker conflictChecker, FlowEngineService flowEngine,
                                 ObjectMapper objectMapper) {
        this.changeMapper = changeMapper;
        this.entryMapper = entryMapper;
        this.conflictChecker = conflictChecker;
        this.flowEngine = flowEngine;
        this.objectMapper = objectMapper;
    }

    /** 提交调课/代课申请：①校验 entryId 归属本人（SELF）②CHANGE 二次校验目标时段无冲突③insert + 启动审批流。 */
    @Transactional
    public Map<String, Object> apply(ScheduleChangeApplyRequest req) {
        Principal p = CurrentPrincipal.require();
        String type = req.type() == null ? "" : req.type().toUpperCase();
        if (!TYPES.contains(type)) {
            throw BizException.conflict("类型须为 CHANGE / SUBSTITUTE / LEAVE_SUBSTITUTE");
        }

        TimetableEntry entry = entryMapper.selectById(req.entryId());
        if (entry == null || !entry.getTeacherId().equals(p.userId())) {
            throw BizException.notFound(); // 防枚举：他人课 / 不存在 一律 40304
        }

        Integer targetDay = req.targetDayOfWeek();
        Integer targetPeriod = req.targetPeriod();
        if ("CHANGE".equals(type)) {
            if (targetDay == null || targetPeriod == null) {
                throw BizException.conflict("调课须指定目标星期与节次");
            }
            String conflict = conflictChecker.check(entry.getTermId(), entry.getWeek(), targetDay, targetPeriod,
                    entry.getTeacherId(), entry.getClassId(), entry.getSpaceId(), entry.getId());
            if (conflict != null) {
                throw BizException.conflict(conflict);
            }
        } else {
            if (req.substituteTeacherId() == null || req.substituteTeacherId().isBlank()) {
                throw BizException.conflict("代课/请假代课须指定代课教师");
            }
            targetDay = entry.getDayOfWeek();
            targetPeriod = entry.getPeriod();
        }

        ScheduleChange change = new ScheduleChange();
        change.setId(Ids.next("schg"));
        change.setApplyNo(Ids.next("SCHG"));
        change.setApplicantId(p.userId());
        change.setTermId(entry.getTermId());
        change.setTargetSlot(writeSlot(targetDay, targetPeriod));
        change.setReason(req.reason());
        change.setStatus("PENDING");
        change.setType(type);
        change.setEntryId(entry.getId());
        change.setSubstituteTeacherId(req.substituteTeacherId());
        change.setCreatedAt(OffsetDateTime.now());
        changeMapper.insert(change);

        Map<String, Object> flow = flowEngine.start("schedule_change", change.getId(), p.userId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("change", view(change));
        out.put("flow", flow);
        return out;
    }

    /** 我的调课/代课（forceSelf），附审批进度（instancesByBusiness 反 N+1）。 */
    public List<Map<String, Object>> my(HttpServletRequest request) {
        QueryWrapper<ScheduleChange> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        List<ScheduleChange> list = changeMapper.selectList(qw);
        Map<String, Map<String, Object>> flows = flowEngine.instancesByBusiness(
                "schedule_change", list.stream().map(ScheduleChange::getId).toList());
        return list.stream().map(e -> {
            Map<String, Object> v = view(e);
            v.put("flow", flows.get(e.getId()));
            return v;
        }).toList();
    }

    /** 详情 + flow（matchRecord 判归属，跨本人 40304 防枚举）。 */
    public Map<String, Object> detail(HttpServletRequest request, String id) {
        ScheduleChange e = changeMapper.selectById(id);
        if (e == null) {
            throw BizException.notFound();
        }
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);
        if (!scope.matchRecord(record(e))) {
            throw BizException.notFound();
        }
        Map<String, Object> v = view(e);
        v.put("flow", flowEngine.instanceByBusiness("schedule_change", id));
        return v;
    }

    private Map<String, Object> record(ScheduleChange e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("applicantId", e.getApplicantId());
        r.put("termId", e.getTermId());
        return r;
    }

    private Map<String, Object> view(ScheduleChange e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("applyNo", e.getApplyNo());
        m.put("applicantId", e.getApplicantId());
        m.put("termId", e.getTermId());
        m.put("targetSlot", parseSlot(e.getTargetSlot()));
        m.put("reason", e.getReason());
        m.put("status", e.getStatus());
        m.put("type", e.getType());
        m.put("entryId", e.getEntryId());
        m.put("substituteTeacherId", e.getSubstituteTeacherId());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private String writeSlot(int dayOfWeek, int period) {
        Map<String, Integer> slot = new LinkedHashMap<>();
        slot.put("dayOfWeek", dayOfWeek);
        slot.put("period", period);
        try {
            return objectMapper.writeValueAsString(slot);
        } catch (Exception e) {
            throw new IllegalStateException("目标时段序列化失败", e);
        }
    }

    private Map<String, Object> parseSlot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
