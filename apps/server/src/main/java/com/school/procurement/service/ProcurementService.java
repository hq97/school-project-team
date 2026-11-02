package com.school.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.scope.Principal;
import com.school.common.util.Ids;
import com.school.procurement.dto.ProcurementCreateRequest;
import com.school.procurement.entity.ProcurementProject;
import com.school.procurement.mapper.ProcurementProjectMapper;
import com.school.workflow.service.FlowEngineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购项目（走审批流，businessType=procurement）。
 *
 * <p>数据范围：ORG（ownerField=creatorId / deptField=creatorDeptId / termField=termId）。
 * 列表/详情均经 {@link ScopeQueryBuilder} 注入范围条件，禁止裸查。</p>
 */
@Service
public class ProcurementService {

    private final ProcurementProjectMapper mapper;
    private final FlowEngineService flowEngine;

    public ProcurementService(ProcurementProjectMapper mapper, FlowEngineService flowEngine) {
        this.mapper = mapper;
        this.flowEngine = flowEngine;
    }

    /** 发起采购需求：写入 creatorId=当前用户、creatorDeptId=主部门、termId=当前学期，同一事务内启动审批流。 */
    @Transactional
    public Map<String, Object> create(ProcurementCreateRequest req) {
        Principal p = CurrentPrincipal.require();
        ProcurementProject e = new ProcurementProject();
        e.setId(Ids.next("proc"));
        e.setApplyNo(Ids.next("PUR"));
        e.setName(req.name());
        e.setCreatorId(p.userId());
        e.setCreatorDeptId(primaryDept(p));
        e.setTermId(p.currentTermId());
        e.setBudget(req.budget());
        e.setStatus("PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        mapper.insert(e);
        flowEngine.start("procurement", e.getId(), p.userId());
        return view(e);
    }

    public List<Map<String, Object>> list(HttpServletRequest request) {
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return mapper.selectList(qw).stream().map(this::view).toList();
    }

    public List<Map<String, Object>> my(HttpServletRequest request) {
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return mapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 详情 + 审批时间线；matchRecord 不通过 → 40304 防枚举。 */
    public Map<String, Object> detail(HttpServletRequest request, String id) {
        ProcurementProject e = mapper.selectById(id);
        if (e == null) {
            throw BizException.notFound();
        }
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);
        if (!scope.matchRecord(record(e))) {
            throw BizException.notFound();
        }
        Map<String, Object> view = view(e);
        view.put("flow", flowEngine.instanceByBusiness("procurement", id));
        return view;
    }

    private Map<String, Object> view(ProcurementProject e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("applyNo", e.getApplyNo());
        m.put("name", e.getName());
        m.put("creatorId", e.getCreatorId());
        m.put("creatorDeptId", e.getCreatorDeptId());
        m.put("termId", e.getTermId());
        m.put("budget", e.getBudget());
        m.put("status", e.getStatus());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private Map<String, Object> record(ProcurementProject e) {
        Map<String, Object> r = new HashMap<>();
        r.put("creatorId", e.getCreatorId());
        r.put("creatorDeptId", e.getCreatorDeptId());
        r.put("termId", e.getTermId());
        return r;
    }

    static String primaryDept(Principal p) {
        return p.deptIds().isEmpty() ? null : p.deptIds().get(0);
    }
}
