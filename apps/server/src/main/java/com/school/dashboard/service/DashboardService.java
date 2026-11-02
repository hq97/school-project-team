package com.school.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.common.scope.DataScopeOptions;
import com.school.common.scope.OwnershipType;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.CurrentPrincipal;
import com.school.dashboard.entity.Alert;
import com.school.dashboard.mapper.AlertMapper;
import com.school.material.entity.MaterialRequisition;
import com.school.material.mapper.MaterialRequisitionMapper;
import com.school.procurement.entity.ProcurementProject;
import com.school.procurement.mapper.ProcurementProjectMapper;
import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import com.school.system.entity.UserDept;
import com.school.system.mapper.UserDeptMapper;
import com.school.workflow.entity.FlowInstance;
import com.school.workflow.mapper.FlowInstanceMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校长端聚合统计（M0201 采购总览 / 异常清单 / M0204 趋势）。
 *
 * <p>硬约束：所有聚合查询经 {@link ScopeQueryBuilder} + {@link ScopeWrapper} 注入范围，禁止裸查。
 * 范围语义：R-PRINCIPAL/R-LOGISTICS（ALL）看全校；R-GRADE（GRADE）看本年级；各表按归属字段
 * （creatorId/applicantId/reporterId + 对应 deptId）经 ScopeQueryBuilder 注入。</p>
 *
 * <p>P0 阈值说明：审批滞留 48h / 报修超时 24h / 单项目预算超阈值 5 万。sys_dict 无数值列，
 * 暂硬编码，待 C10 调度任务落 alert 表后统一从配置读取。</p>
 */
@Service
public class DashboardService {

    private static final int APPROVAL_STALL_HOURS = 48;
    private static final int REPAIR_OVERDUE_HOURS = 24;
    private static final BigDecimal BUDGET_OVER_THRESHOLD = new BigDecimal("50000");

    private final ProcurementProjectMapper procurementMapper;
    private final MaterialRequisitionMapper materialMapper;
    private final RepairTicketMapper repairMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final UserDeptMapper userDeptMapper;
    private final AlertMapper alertMapper;

    public DashboardService(ProcurementProjectMapper procurementMapper,
                            MaterialRequisitionMapper materialMapper,
                            RepairTicketMapper repairMapper,
                            FlowInstanceMapper flowInstanceMapper,
                            UserDeptMapper userDeptMapper,
                            AlertMapper alertMapper) {
        this.procurementMapper = procurementMapper;
        this.materialMapper = materialMapper;
        this.repairMapper = repairMapper;
        this.flowInstanceMapper = flowInstanceMapper;
        this.userDeptMapper = userDeptMapper;
        this.alertMapper = alertMapper;
    }

    /** 采购总览：当前学期项目数、预算总额、执行金额、状态分布。 */
    public Map<String, Object> procurementSummary(HttpServletRequest request) {
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);

        QueryWrapper<ProcurementProject> totalQw = new QueryWrapper<>();
        ScopeWrapper.apply(totalQw, scope.buildWhere(null));
        totalQw.select("COUNT(*) AS cnt", "COALESCE(SUM(\"budget\"),0) AS total");
        List<Map<String, Object>> totalRows = procurementMapper.selectMaps(totalQw);
        long count = totalRows.isEmpty() ? 0 : ((Number) totalRows.get(0).get("cnt")).longValue();
        BigDecimal budgetTotal = totalRows.isEmpty() ? BigDecimal.ZERO : toBigDecimal(totalRows.get(0).get("total"));

        QueryWrapper<ProcurementProject> execQw = new QueryWrapper<>();
        ScopeWrapper.apply(execQw, scope.buildWhere(null));
        execQw.in("\"status\"", "APPROVED", "COMPLETED");
        execQw.select("COALESCE(SUM(\"budget\"),0) AS executed");
        List<Map<String, Object>> execRows = procurementMapper.selectMaps(execQw);
        BigDecimal executed = execRows.isEmpty() ? BigDecimal.ZERO : toBigDecimal(execRows.get(0).get("executed"));

        QueryWrapper<ProcurementProject> statusQw = new QueryWrapper<>();
        ScopeWrapper.apply(statusQw, scope.buildWhere(null));
        statusQw.select("\"status\" AS status", "COUNT(*) AS cnt").groupBy("\"status\"");
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Map<String, Object> r : procurementMapper.selectMaps(statusQw)) {
            statusCounts.put((String) r.get("status"), ((Number) r.get("cnt")).longValue());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectCount", count);
        out.put("budgetTotal", budgetTotal);
        out.put("executedAmount", executed);
        out.put("statusCounts", statusCounts);
        return out;
    }

    /** 异常清单：alert 表 open 记录 + 实时计算（审批滞留/预算超阈值/报修超时），按范围过滤。 */
    public List<Map<String, Object>> alerts(HttpServletRequest request) {
        Principal p = CurrentPrincipal.require();
        ScopeQueryBuilder alertScope = ScopeQueryBuilder.fromRequest(request);

        List<Map<String, Object>> list = new ArrayList<>();

        QueryWrapper<Alert> alertQw = new QueryWrapper<>();
        ScopeWrapper.apply(alertQw, alertScope.buildWhere(null));
        alertQw.eq("\"status\"", "OPEN").orderByDesc("\"createdAt\"");
        for (Alert a : alertMapper.selectList(alertQw)) {
            list.add(alertView(a));
        }

        list.addAll(approvalStalls(p, alertScope));
        list.addAll(budgetOver(p));
        list.addAll(repairOverdue(p));
        return list;
    }

    /** 趋势：采购金额 / 领用频次 / 报修量，dim=TERM|MONTH（自然月仅展示维度，鉴权口径不变）。 */
    public Map<String, Object> trends(HttpServletRequest request, String dim) {
        Principal p = CurrentPrincipal.require();
        String d = "MONTH".equals(dim) ? "MONTH" : "TERM";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dim", d);
        out.put("procurement", procurementTrend(p, d));
        out.put("material", materialTrend(p, d));
        out.put("repair", repairTrend(p, d));
        return out;
    }

    // ---- 实时异常 ----

    private List<Map<String, Object>> approvalStalls(Principal p, ScopeQueryBuilder alertScope) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(APPROVAL_STALL_HOURS);
        List<FlowInstance> pending = flowInstanceMapper.selectList(
                new LambdaQueryWrapper<FlowInstance>()
                        .eq(FlowInstance::getStatus, "PENDING")
                        .lt(FlowInstance::getCreatedAt, cutoff));
        if (pending.isEmpty()) {
            return List.of();
        }
        List<String> creatorIds = pending.stream().map(FlowInstance::getCreatorId).distinct().toList();
        Map<String, String> deptByUser = resolveDeptByUser(creatorIds);

        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowInstance inst : pending) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("ownerId", inst.getCreatorId());
            rec.put("ownerDeptId", deptByUser.get(inst.getCreatorId()));
            rec.put("termId", null);
            if (!alertScope.matchRecord(rec)) {
                continue;
            }
            out.add(alertItem("APPROVAL_STALL", inst.getBusinessType(), inst.getBusinessId(),
                    inst.getCreatorId(), deptByUser.get(inst.getCreatorId()), null,
                    "审批滞留超过 " + APPROVAL_STALL_HOURS + " 小时", inst.getCreatedAt()));
        }
        return out;
    }

    private List<Map<String, Object>> budgetOver(Principal p) {
        ScopeQueryBuilder scope = scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId");
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null));
        qw.in("\"status\"", "APPROVED", "COMPLETED");
        qw.ge("\"budget\"", BUDGET_OVER_THRESHOLD);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProcurementProject e : procurementMapper.selectList(qw)) {
            out.add(alertItem("BUDGET_OVER", "procurement", e.getId(), e.getCreatorId(),
                    e.getCreatorDeptId(), e.getTermId(), "采购预算超阈值：" + e.getName(), e.getCreatedAt()));
        }
        return out;
    }

    private List<Map<String, Object>> repairOverdue(Principal p) {
        ScopeQueryBuilder scope = scopeFor(p, "repair_ticket", "reporterId", "reporterDeptId", "termId");
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null));
        qw.in("\"status\"", "PENDING", "ASSIGNED", "PROCESSING");
        qw.lt("\"createdAt\"", OffsetDateTime.now().minusHours(REPAIR_OVERDUE_HOURS));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RepairTicket e : repairMapper.selectList(qw)) {
            out.add(alertItem("REPAIR_OVERDUE", "repair", e.getId(), e.getReporterId(),
                    e.getReporterDeptId(), e.getTermId(), "报修超时未处理：" + e.getTicketNo(), e.getCreatedAt()));
        }
        return out;
    }

    // ---- 趋势 ----

    private List<Map<String, Object>> procurementTrend(Principal p, String dim) {
        ScopeQueryBuilder scope = scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId");
        String bucket = bucketExpr(dim);
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null));
        qw.select(bucket + " AS bucket", "COALESCE(SUM(\"budget\"),0) AS total").groupBy(bucket).orderByAsc(bucket);
        return procurementMapper.selectMaps(qw);
    }

    private List<Map<String, Object>> materialTrend(Principal p, String dim) {
        ScopeQueryBuilder scope = scopeFor(p, "material_requisition", "applicantId", "applicantDeptId", "termId");
        String bucket = bucketExpr(dim);
        QueryWrapper<MaterialRequisition> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null));
        qw.select(bucket + " AS bucket", "COUNT(*) AS cnt").groupBy(bucket).orderByAsc(bucket);
        return materialMapper.selectMaps(qw);
    }

    private List<Map<String, Object>> repairTrend(Principal p, String dim) {
        ScopeQueryBuilder scope = scopeFor(p, "repair_ticket", "reporterId", "reporterDeptId", "termId");
        String bucket = bucketExpr(dim);
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scope.buildWhere(null));
        qw.select(bucket + " AS bucket", "COUNT(*) AS cnt").groupBy(bucket).orderByAsc(bucket);
        return repairMapper.selectMaps(qw);
    }

    // ---- 辅助 ----

    private ScopeQueryBuilder scopeFor(Principal p, String resource, String owner, String dept, String term) {
        DataScopeOptions opts = new DataScopeOptions(resource, OwnershipType.ORG, null, owner, dept, "", term, false, false);
        return ScopeQueryBuilder.of(p, opts);
    }

    private String bucketExpr(String dim) {
        return "MONTH".equals(dim)
                ? "to_char(date_trunc('month', \"createdAt\"), 'YYYY-MM')"
                : "\"termId\"";
    }

    private Map<String, String> resolveDeptByUser(List<String> userIds) {
        Map<String, String> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        List<UserDept> rows = userDeptMapper.selectList(
                new LambdaQueryWrapper<UserDept>().in(UserDept::getUserId, userIds));
        for (UserDept ud : rows) {
            out.putIfAbsent(ud.getUserId(), ud.getDeptId());
        }
        return out;
    }

    private Map<String, Object> alertItem(String type, String bizType, String bizId, String ownerId,
                                          String ownerDeptId, String termId, String title, OffsetDateTime createdAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertType", type);
        m.put("bizType", bizType);
        m.put("bizId", bizId);
        m.put("ownerId", ownerId);
        m.put("ownerDeptId", ownerDeptId);
        m.put("termId", termId);
        m.put("level", "WARNING");
        m.put("status", "OPEN");
        m.put("title", title);
        m.put("createdAt", createdAt);
        return m;
    }

    private Map<String, Object> alertView(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertType", a.getAlertType());
        m.put("bizType", a.getBizType());
        m.put("bizId", a.getBizId());
        m.put("ownerId", a.getOwnerId());
        m.put("ownerDeptId", a.getOwnerDeptId());
        m.put("termId", a.getTermId());
        m.put("level", a.getLevel());
        m.put("status", a.getStatus());
        m.put("title", a.getAlertType());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(o.toString());
    }
}
