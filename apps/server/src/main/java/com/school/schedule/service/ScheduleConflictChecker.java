package com.school.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.common.scope.DataScopeOptions;
import com.school.common.security.CurrentPrincipal;
import com.school.common.scope.OwnershipType;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.schedule.entity.TimetableEntry;
import com.school.schedule.mapper.TimetableEntryMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 时段冲突判定器（单一真源）：同教师 / 同班级 / 同教室，在相同
 * {@code (termId, week, dayOfWeek, period)} 已存在即冲突。
 *
 * <p>排课（M0602）、调课申请二次校验、调课生效回调（AC-H3-2）共用本判定器，保证同一口径。</p>
 *
 * <p><b>范围口径</b>：冲突是全校范围的完整性不变量（跨教师/跨班/跨教室都可能冲突），
 * 故用 {@link ScopeQueryBuilder}(PUBLIC) 注入空范围（全校口径），再按精确时段过滤——仍走
 * 数据层唯一出口 {@link ScopeWrapper}（门禁4 规则③）。</p>
 */
@Component
public class ScheduleConflictChecker {

    private final TimetableEntryMapper mapper;

    public ScheduleConflictChecker(TimetableEntryMapper mapper) {
        this.mapper = mapper;
    }

    /** 返回冲突描述；无冲突返回 null。 */
    public String check(String termId, int week, int dayOfWeek, int period,
                        String teacherId, String classId, String spaceId, String excludeEntryId) {
        ScopeQueryBuilder scope = ScopeQueryBuilder.of(CurrentPrincipal.require(),
                new DataScopeOptions("timetable_entry", OwnershipType.PUBLIC, null, "", "", "", "", false, false));

        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null)); // PUBLIC → 空范围（全校口径）
        qw.eq("\"termId\"", termId)
                .eq("\"week\"", week)
                .eq("\"dayOfWeek\"", dayOfWeek)
                .eq("\"period\"", period);
        qw.and(w -> w.eq("\"teacherId\"", teacherId)
                .or().eq("\"classId\"", classId)
                .or().eq("\"spaceId\"", spaceId));
        if (excludeEntryId != null && !excludeEntryId.isEmpty()) {
            qw.ne("\"id\"", excludeEntryId);
        }

        List<TimetableEntry> hits = mapper.selectList(qw);
        if (hits.isEmpty()) {
            return null;
        }
        TimetableEntry e = hits.get(0);
        if (e.getTeacherId() != null && e.getTeacherId().equals(teacherId)) {
            return "教师已在该时段有课";
        }
        if (e.getClassId() != null && e.getClassId().equals(classId)) {
            return "班级已在该时段有课";
        }
        if (e.getSpaceId() != null && e.getSpaceId().equals(spaceId)) {
            return "场地已在该时段被占用";
        }
        return "该时段已被占用";
    }
}
