package com.school.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.school.asset.entity.Space;
import com.school.asset.entity.SpaceOccupancy;
import com.school.asset.mapper.SpaceMapper;
import com.school.asset.mapper.SpaceOccupancyMapper;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.util.Ids;
import com.school.schedule.dto.EntryCreateRequest;
import com.school.schedule.dto.PublishRequest;
import com.school.schedule.entity.ScheduleVersion;
import com.school.schedule.entity.TimetableEntry;
import com.school.schedule.mapper.ScheduleVersionMapper;
import com.school.schedule.mapper.TimetableEntryMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教务域排课（M06）+ 查课（M07）。docs/11 REQ-S1/S2/S3。
 *
 * <p>冲突判定统一走 {@link ScheduleConflictChecker}（单一真源）；列表/聚合查询统一走
 * {@link ScopeQueryBuilder} + {@link ScopeWrapper}（门禁4 规则③）。</p>
 */
@Service
public class ScheduleService {

    private static final int MIN_WEEK = 1;
    private static final int MAX_WEEK = 20;
    private static final String DRAFT_VERSION = "";

    private final TimetableEntryMapper entryMapper;
    private final ScheduleVersionMapper versionMapper;
    private final SpaceMapper spaceMapper;
    private final SpaceOccupancyMapper occupancyMapper;
    private final ScheduleConflictChecker conflictChecker;

    public ScheduleService(TimetableEntryMapper entryMapper, ScheduleVersionMapper versionMapper,
                           SpaceMapper spaceMapper, SpaceOccupancyMapper occupancyMapper,
                           ScheduleConflictChecker conflictChecker) {
        this.entryMapper = entryMapper;
        this.versionMapper = versionMapper;
        this.spaceMapper = spaceMapper;
        this.occupancyMapper = occupancyMapper;
        this.conflictChecker = conflictChecker;
    }

    /** 可选场地（PUBLIC，仅菜单控制）。 */
    public List<Map<String, Object>> spaces() {
        QueryWrapper<Space> qw = new QueryWrapper<>();
        qw.eq("\"status\"", "AVAILABLE");
        qw.orderByAsc("\"spaceType\"");
        return spaceMapper.selectList(qw).stream().map(this::spaceView).toList();
    }

    /** 手动排课：服务端冲突校验（同教师/同班级/同教室 同时段）→ 409；通过则 insert。 */
    @Transactional
    public Map<String, Object> arrange(EntryCreateRequest req) {
        Principal p = CurrentPrincipal.require();
        String termId = (req.termId() == null || req.termId().isBlank()) ? p.currentTermId() : req.termId();
        validateSlot(termId, req.week(), req.dayOfWeek(), req.period());

        String conflict = conflictChecker.check(termId, req.week(), req.dayOfWeek(), req.period(),
                req.teacherId(), req.classId(), req.spaceId(), null);
        if (conflict != null) {
            throw BizException.conflict(conflict);
        }

        TimetableEntry e = new TimetableEntry();
        e.setId(Ids.next("te"));
        e.setVersionId(DRAFT_VERSION); // 发布前占位，发布时回写 versionId
        e.setSpaceId(req.spaceId());
        e.setTeacherId(req.teacherId());
        e.setClassId(req.classId());
        e.setSubject(req.subject());
        e.setTermId(termId);
        e.setWeek(req.week());
        e.setDayOfWeek(req.dayOfWeek());
        e.setPeriod(req.period());
        entryMapper.insert(e);
        return view(e);
    }

    /** 课表发布：乐观锁 + 生成新版本 + 回写 versionId + 写场地占用。 */
    @Transactional
    public Map<String, Object> publish(PublishRequest req) {
        if (req.effectiveWeek() < MIN_WEEK || req.effectiveWeek() > MAX_WEEK) {
            throw BizException.conflict("生效周次须在 " + MIN_WEEK + "~" + MAX_WEEK + " 之间");
        }

        List<ScheduleVersion> existing = versionMapper.selectList(
                new QueryWrapper<ScheduleVersion>()
                        .eq("\"termId\"", req.termId())
                        .eq("\"effectiveWeek\"", req.effectiveWeek())
                        .orderByDesc("\"version\""));
        int currentMax = existing.isEmpty() ? 0 : existing.get(0).getVersion();
        if (req.baseVersion() != null && req.baseVersion() < currentMax) {
            throw BizException.conflict("课表版本已更新，请刷新后重试");
        }
        int newVersion = currentMax + 1;

        ScheduleVersion sv = new ScheduleVersion();
        sv.setId(Ids.next("sv"));
        sv.setTermId(req.termId());
        sv.setEffectiveWeek(req.effectiveWeek());
        sv.setVersion(newVersion);
        sv.setStatus("PUBLISHED");
        sv.setPublishedAt(OffsetDateTime.now());
        versionMapper.insert(sv);

        entryMapper.update(null, new UpdateWrapper<TimetableEntry>()
                .eq("\"termId\"", req.termId())
                .eq("\"week\"", req.effectiveWeek())
                .set("\"versionId\"", sv.getId()));

        syncOccupancy(req.termId(), req.effectiveWeek(), p().userId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", sv.getId());
        out.put("termId", sv.getTermId());
        out.put("effectiveWeek", sv.getEffectiveWeek());
        out.put("version", sv.getVersion());
        out.put("status", sv.getStatus());
        return out;
    }

    /** 我的课表（forceSelf：teacherId=当前用户）。 */
    public List<Map<String, Object>> myTimetable(HttpServletRequest request, Integer week) {
        Map<String, Object> extra = new HashMap<>();
        if (week != null) {
            extra.put("week", week);
        }
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByAsc("\"dayOfWeek\"").orderByAsc("\"period\"");
        return entryMapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 班级课表（班主任按 head_teacher 收窄到本班；普通教师按数据范围）。 */
    public List<Map<String, Object>> classTimetable(HttpServletRequest request, String classId, Integer week) {
        Map<String, Object> extra = new HashMap<>();
        if (classId != null && !classId.isBlank()) {
            extra.put("classId", classId);
        }
        if (week != null) {
            extra.put("week", week);
        }
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByAsc("\"dayOfWeek\"").orderByAsc("\"period\"");
        return entryMapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 全校课表（BTN-SCH-VIEW-ALL 门禁，R-DEAN 等 ALL 范围）。 */
    public List<Map<String, Object>> schoolTimetable(HttpServletRequest request, Integer week) {
        Map<String, Object> extra = new HashMap<>();
        if (week != null) {
            extra.put("week", week);
        }
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByAsc("\"classId\"").orderByAsc("\"dayOfWeek\"").orderByAsc("\"period\"");
        return entryMapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 把指定 termId+week 的课表写入场地占用（SCHEDULE/EFFECTIVE），重复发布先清旧占用。 */
    @Transactional
    public void syncOccupancy(String termId, int week, String operatorId) {
        occupancyMapper.delete(new QueryWrapper<SpaceOccupancy>()
                .eq("\"termId\"", termId)
                .eq("\"week\"", week)
                .eq("\"source\"", "SCHEDULE"));

        List<TimetableEntry> entries = entryMapper.selectList(
                new QueryWrapper<TimetableEntry>()
                        .eq("\"termId\"", termId)
                        .eq("\"week\"", week)
                        .isNotNull("\"spaceId\""));

        for (TimetableEntry e : entries) {
            SpaceOccupancy occ = new SpaceOccupancy();
            occ.setId(Ids.next("occ"));
            occ.setSpaceId(e.getSpaceId());
            occ.setTermId(termId);
            occ.setWeek(week);
            occ.setDayOfWeek(e.getDayOfWeek());
            occ.setPeriod(e.getPeriod());
            occ.setSource("SCHEDULE");
            occ.setBizRefId(e.getId());
            occ.setStatus("EFFECTIVE");
            occ.setCreatedBy(operatorId);
            occ.setCreatedAt(OffsetDateTime.now());
            occupancyMapper.insert(occ);
        }
    }

    private void validateSlot(String termId, int week, int dayOfWeek, int period) {
        if (week < MIN_WEEK || week > MAX_WEEK) {
            throw BizException.conflict("周次须在 " + MIN_WEEK + "~" + MAX_WEEK + " 之间");
        }
        if (dayOfWeek < 1 || dayOfWeek > 5) {
            throw BizException.conflict("星期须在 1~5 之间");
        }
        if (period < 1 || period > 10) {
            throw BizException.conflict("节次须在 1~10 之间");
        }
    }

    private Principal p() {
        return CurrentPrincipal.require();
    }

    private Map<String, Object> view(TimetableEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("versionId", e.getVersionId());
        m.put("spaceId", e.getSpaceId());
        m.put("teacherId", e.getTeacherId());
        m.put("classId", e.getClassId());
        m.put("subject", e.getSubject());
        m.put("termId", e.getTermId());
        m.put("week", e.getWeek());
        m.put("dayOfWeek", e.getDayOfWeek());
        m.put("period", e.getPeriod());
        m.put("substituteTeacherId", e.getSubstituteTeacherId());
        return m;
    }

    private Map<String, Object> spaceView(Space s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("spaceName", s.getSpaceName());
        m.put("spaceType", s.getSpaceType());
        m.put("capacity", s.getCapacity());
        m.put("status", s.getStatus());
        m.put("openScope", s.getOpenScope());
        return m;
    }
}
