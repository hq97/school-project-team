package com.school.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.util.Ids;
import com.school.schedule.dto.TeacherCourseRequest;
import com.school.schedule.entity.TeacherCourse;
import com.school.schedule.mapper.TeacherCourseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教务域「F. 排课管理」REQ-F1 教师任课表。
 *
 * <p>数据范围 ORG（ownerField=teacherId / deptField=classId）：
 * R-TEACHER（SELF）仅本人任课、R-DEAN（ALL）全校任课。写操作（改/删）在服务端经
 * {@link ScopeQueryBuilder#matchRecord(Map)} 判定归属，越权一律 40304 防枚举（按钮门禁先行拦截，
 * 此处为纵深防御）。列表/聚合查询统一走 {@link ScopeQueryBuilder} + {@link ScopeWrapper}（门禁4 规则③）。</p>
 */
@Service
public class TeacherCourseService {

    private final TeacherCourseMapper mapper;

    public TeacherCourseService(TeacherCourseMapper mapper) {
        this.mapper = mapper;
    }

    /** 任课列表（?termId= 可选，缺省当前学期），按归属范围收窄。 */
    public List<Map<String, Object>> list(HttpServletRequest request, String termId) {
        Principal p = CurrentPrincipal.require();
        Map<String, Object> extra = new HashMap<>();
        extra.put("termId", (termId == null || termId.isBlank()) ? p.currentTermId() : termId);
        QueryWrapper<TeacherCourse> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByAsc("\"classId\"").orderByAsc("\"subject\"");
        return mapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 新建任课：唯一索引防重复任课，冲突 → 40900。 */
    @Transactional
    public Map<String, Object> create(TeacherCourseRequest req) {
        Principal p = CurrentPrincipal.require();
        String termId = normalizeTerm(req.termId(), p);
        assertNotDuplicate(req.teacherId(), req.classId(), req.subject(), termId, null);

        TeacherCourse e = new TeacherCourse();
        e.setId(Ids.next("tc"));
        e.setTeacherId(req.teacherId());
        e.setClassId(req.classId());
        e.setSubject(req.subject());
        e.setTermId(termId);
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        mapper.insert(e);
        return view(e);
    }

    /** 更新任课：matchRecord 判归属（越权 40304）+ 唯一索引防重复。 */
    @Transactional
    public Map<String, Object> update(HttpServletRequest request, String id, TeacherCourseRequest req) {
        TeacherCourse e = requireOwned(request, id);
        Principal p = CurrentPrincipal.require();
        String termId = normalizeTerm(req.termId(), p);
        assertNotDuplicate(req.teacherId(), req.classId(), req.subject(), termId, id);

        e.setTeacherId(req.teacherId());
        e.setClassId(req.classId());
        e.setSubject(req.subject());
        e.setTermId(termId);
        e.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(e);
        return view(e);
    }

    /** 删除任课：matchRecord 判归属（越权 40304）。 */
    @Transactional
    public void delete(HttpServletRequest request, String id) {
        TeacherCourse e = requireOwned(request, id);
        mapper.deleteById(e.getId());
    }

    /** 加载记录 + matchRecord 判定归属；不存在 / 越权一律 40304（防枚举）。 */
    private TeacherCourse requireOwned(HttpServletRequest request, String id) {
        TeacherCourse e = mapper.selectById(id);
        if (e == null) {
            throw BizException.notFound();
        }
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);
        if (!scope.matchRecord(record(e))) {
            throw BizException.notFound();
        }
        return e;
    }

    private void assertNotDuplicate(String teacherId, String classId, String subject, String termId, String excludeId) {
        QueryWrapper<TeacherCourse> qw = new QueryWrapper<>();
        qw.eq("\"teacherId\"", teacherId)
                .eq("\"classId\"", classId)
                .eq("\"subject\"", subject)
                .eq("\"termId\"", termId);
        if (excludeId != null) {
            qw.ne("\"id\"", excludeId);
        }
        Long exists = mapper.selectCount(qw);
        if (exists != null && exists > 0) {
            throw BizException.conflict("该教师在此班级已存在该学科任课，请勿重复添加");
        }
    }

    private String normalizeTerm(String termId, Principal p) {
        return (termId == null || termId.isBlank()) ? p.currentTermId() : termId;
    }

    private Map<String, Object> record(TeacherCourse e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("teacherId", e.getTeacherId());
        r.put("classId", e.getClassId());
        r.put("termId", e.getTermId());
        return r;
    }

    private Map<String, Object> view(TeacherCourse e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("teacherId", e.getTeacherId());
        m.put("classId", e.getClassId());
        m.put("subject", e.getSubject());
        m.put("termId", e.getTermId());
        m.put("createdAt", e.getCreatedAt());
        m.put("updatedAt", e.getUpdatedAt());
        return m;
    }
}
