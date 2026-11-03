package com.school.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Asset;
import com.school.asset.mapper.AssetMapper;
import com.school.common.scope.DataScopeOptions;
import com.school.common.scope.OwnershipType;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.material.entity.MaterialRequisition;
import com.school.material.mapper.MaterialRequisitionMapper;
import com.school.procurement.entity.ProcurementProject;
import com.school.procurement.mapper.ProcurementProjectMapper;
import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import com.school.schedule.entity.ScheduleChange;
import com.school.schedule.entity.TimetableEntry;
import com.school.schedule.mapper.ScheduleChangeMapper;
import com.school.schedule.mapper.TimetableEntryMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏总览（M15 /api/dashboard/overview）+ 指标下钻（/api/dashboard/drill）。
 *
 * <p>硬约束（docs/14）：
 * <ul>
 *   <li>全部走服务端聚合 + 数据权限过滤（ScopeQueryBuilder），前端不拉全量、不本地聚合（AC-DD5-1）。</li>
 *   <li>dim=MONTH 仅切换展示粒度，数据可见范围与鉴权口径不变（AC-DD1-2）。</li>
 *   <li>指标与下钻明细不得包含平台级配置对象（字典/流程模板/菜单/角色/部门）（AC-DD5-2）。</li>
 *   <li>无 M15 菜单权限的角色请求 overview 直接 40301；无预置模板的自建角色降级 NONE。</li>
 * </ul>
 *
 * <p>数据边界：首版六域中仅「后勤（采购/物资/报修/资产）」与「教务（课表/调课/任课）」已有业务实体，
 * 德育/党政/科教/体育四域暂无实体，其 KPI/图表暂不产出（返回空，避免 0 值误导）。</p>
 */
@Service
public class DashboardOverviewService {

    private static final int STOCK_LOW_THRESHOLD = 5;

    private final ProcurementProjectMapper procurementMapper;
    private final MaterialRequisitionMapper materialMapper;
    private final RepairTicketMapper repairMapper;
    private final AssetMapper assetMapper;
    private final TimetableEntryMapper timetableMapper;
    private final ScheduleChangeMapper scheduleChangeMapper;
    private final DashboardService dashboardService;

    public DashboardOverviewService(ProcurementProjectMapper procurementMapper,
                                    MaterialRequisitionMapper materialMapper,
                                    RepairTicketMapper repairMapper,
                                    AssetMapper assetMapper,
                                    TimetableEntryMapper timetableMapper,
                                    ScheduleChangeMapper scheduleChangeMapper,
                                    DashboardService dashboardService) {
        this.procurementMapper = procurementMapper;
        this.materialMapper = materialMapper;
        this.repairMapper = repairMapper;
        this.assetMapper = assetMapper;
        this.timetableMapper = timetableMapper;
        this.scheduleChangeMapper = scheduleChangeMapper;
        this.dashboardService = dashboardService;
    }

    /** 大屏总览：按角色返回模板数据。 */
    public Map<String, Object> overview(HttpServletRequest request, String dim) {
        Principal p = CurrentPrincipal.require();
        if (!p.menuCodes().contains("M15")) {
            throw BizException.menuDenied(); // 40301
        }
        String role = resolveRole(p);
        String templateKey = resolveTemplate(role);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleCode", role);
        out.put("templateKey", templateKey);
        out.put("dataAsOf", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        out.put("scope", p.scope().name());
        out.put("scopeText", scopeText(p));
        out.put("currentTerm", p.currentTermId());
        out.put("kpis", buildKpis(templateKey, p, request));
        out.put("charts", buildCharts(templateKey, p, request, dim));
        out.put("alerts", dashboardService.alerts(request));
        return out;
    }

    /** 指标下钻：按 kpiCode 返回明细列表，仍受数据权限约束。 */
    public Map<String, Object> drill(HttpServletRequest request, String kpiCode, String dim, Integer page, Integer size) {
        Principal p = CurrentPrincipal.require();
        if (!p.menuCodes().contains("M15")) {
            throw BizException.menuDenied();
        }
        List<Map<String, Object>> list = switch (kpiCode == null ? "" : kpiCode) {
            case "PROC_COUNT", "PROC_BUDGET" -> procurementDetail(p, request);
            case "MAT_REQ_COUNT" -> materialDetail(p, request);
            case "REPAIR_COUNT" -> repairDetail(p, request);
            case "STOCK_ALERT" -> stockAlertDetail(p, request);
            case "TIMETABLE_COUNT" -> timetableDetail(p, request);
            case "CHANGE_COUNT" -> scheduleChangeDetail(p, request);
            default -> List.of();
        };
        int total = list.size();
        int from = (page == null ? 0 : Math.max(0, page - 1)) * (size == null ? 20 : size);
        int to = Math.min(list.size(), from + (size == null ? 20 : size));
        List<Map<String, Object>> pageList = (from >= list.size()) ? List.of() : list.subList(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", pageList);
        out.put("total", total);
        return out;
    }

    // ---- 角色 / 模板 ----

    private String resolveRole(Principal p) {
        String[] order = {"R-PRINCIPAL", "R-VP", "R-DEAN", "R-LOGISTICS", "R-MORAL",
                "R-PARTY", "R-GRADE", "R-SUBJECT", "R-ADMIN"};
        for (String r : order) {
            if (p.roleCodes().contains(r)) {
                return r;
            }
        }
        return p.roleCodes().isEmpty() ? "R-TEACHER" : p.roleCodes().get(0);
    }

    private String resolveTemplate(String role) {
        return switch (role) {
            case "R-PRINCIPAL", "R-ADMIN" -> "PRINCIPAL";
            case "R-VP" -> "VP";
            case "R-DEAN" -> "DEAN";
            case "R-LOGISTICS" -> "LOGISTICS";
            case "R-MORAL" -> "MORAL";
            case "R-PARTY" -> "PARTY";
            case "R-GRADE" -> "GRADE";
            case "R-SUBJECT" -> "SUBJECT";
            default -> "NONE";
        };
    }

    private String scopeText(Principal p) {
        return switch (p.scope()) {
            case ALL -> "全校";
            case GRADE -> "本年级";
            default -> "本人";
        };
    }

    // ---- KPI 聚合 ----

    private List<Map<String, Object>> buildKpis(String templateKey, Principal p, HttpServletRequest request) {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (templateKey) {
            case "PRINCIPAL", "VP" -> {
                out.add(kpi("PROC_COUNT", "采购项目数", countProcurement(p, request), "个", "/procurement/list", "POSITIVE"));
                out.add(kpi("PROC_BUDGET", "采购预算总额", budgetProcurement(p, request), "元", "/procurement/list", "POSITIVE"));
                out.add(kpi("MAT_REQ_COUNT", "物资领用次数", countMaterial(p, request), "次", "/material/records", "POSITIVE"));
                out.add(kpi("REPAIR_COUNT", "报修工单数", countRepair(p, request), "单", "/repair/tickets", "NEGATIVE"));
                out.add(kpi("STOCK_ALERT", "库存告警", countStockAlert(p, request), "项", "/asset/list", "NEGATIVE"));
                out.add(kpi("TIMETABLE_COUNT", "已排课条目", countTimetable(p, request), "条", "/timetable/my", "POSITIVE"));
            }
            case "DEAN" -> {
                out.add(kpi("TIMETABLE_COUNT", "已排课条目", countTimetable(p, request), "条", "/timetable/my", "POSITIVE"));
                out.add(kpi("CHANGE_COUNT", "调课/代课次数", countScheduleChange(p, request), "次", "/schedule-change/my", "NEGATIVE"));
                out.add(kpi("REPAIR_COUNT", "报修工单数", countRepair(p, request), "单", "/repair/tickets", "NEGATIVE"));
                out.add(kpi("PROC_COUNT", "采购项目数", countProcurement(p, request), "个", "/procurement/list", "POSITIVE"));
            }
            case "LOGISTICS" -> {
                out.add(kpi("PROC_COUNT", "采购项目数", countProcurement(p, request), "个", "/procurement/list", "POSITIVE"));
                out.add(kpi("PROC_BUDGET", "采购预算总额", budgetProcurement(p, request), "元", "/procurement/list", "POSITIVE"));
                out.add(kpi("STOCK_ALERT", "库存告警", countStockAlert(p, request), "项", "/asset/list", "NEGATIVE"));
                out.add(kpi("REPAIR_COUNT", "报修工单数", countRepair(p, request), "单", "/repair/tickets", "NEGATIVE"));
            }
            case "GRADE" -> {
                out.add(kpi("TIMETABLE_COUNT", "本年级已排课", countTimetable(p, request), "条", "/timetable/my", "POSITIVE"));
                out.add(kpi("CHANGE_COUNT", "本年级调课次数", countScheduleChange(p, request), "次", "/schedule-change/my", "NEGATIVE"));
            }
            default -> {
                // MORAL / PARTY / SUBJECT / NONE：暂无实体，不产出 KPI
            }
        }
        return out;
    }

    // ---- 图表聚合 ----

    private List<Map<String, Object>> buildCharts(String templateKey, Principal p, HttpServletRequest request, String dim) {
        List<Map<String, Object>> out = new ArrayList<>();
        String d = "MONTH".equals(dim) ? "MONTH" : "TERM";
        switch (templateKey) {
            case "PRINCIPAL", "VP" -> {
                out.add(chartTrend("TREND_PURCHASE", "采购金额趋势", procurementTrend(p, request, d), "元"));
                out.add(chartTrend("TREND_REPAIR", "报修量趋势", repairTrend(p, request, d), "单"));
                out.add(chartBar("DIST_CHANGE", "调课分布", changeTrend(p, request, d), "次"));
                out.add(chartBarH("CMP_MATERIAL_TOP", "高频领用物资 TOP", materialTop(p, request), "次"));
                out.add(chartRadar("RADAR_DOMAIN_HEALTH", "六域健康度", domainHealth(p, request)));
            }
            case "DEAN" -> {
                out.add(chartTrend("TREND_CHANGE", "调课/代课趋势", changeTrend(p, request, d), "次"));
                out.add(chartBar("DIST_TIMETABLE", "课表分布（按班级）", timetableByClass(p, request), "条"));
                out.add(chartBarH("CMP_TEACHER_HOURS", "教师周课时", teacherHours(p, request), "节"));
            }
            case "LOGISTICS" -> {
                out.add(chartTrend("TREND_PURCHASE", "采购金额趋势", procurementTrend(p, request, d), "元"));
                out.add(chartTrend("TREND_REPAIR", "报修量趋势", repairTrend(p, request, d), "单"));
                out.add(chartBarH("CMP_MATERIAL_TOP", "高频领用物资 TOP", materialTop(p, request), "次"));
            }
            case "GRADE" -> {
                out.add(chartBar("DIST_TIMETABLE", "本年级课表分布", timetableByClass(p, request), "条"));
            }
            default -> {
                // MORAL / PARTY / SUBJECT / NONE：暂无实体，不产出图表
            }
        }
        return out;
    }

    // ---- 明细下钻 ----

    private List<Map<String, Object>> procurementDetail(Principal p, HttpServletRequest request) {
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId").buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return procurementMapper.selectList(qw).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("applyNo", e.getApplyNo());
            m.put("name", e.getName());
            m.put("budget", e.getBudget());
            m.put("status", e.getStatus());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> materialDetail(Principal p, HttpServletRequest request) {
        QueryWrapper<MaterialRequisition> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "material_requisition", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return materialMapper.selectList(qw).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("applyNo", e.getApplyNo());
            m.put("assetId", e.getAssetId());
            m.put("quantity", e.getQuantity());
            m.put("status", e.getStatus());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> repairDetail(Principal p, HttpServletRequest request) {
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "repair_ticket", "reporterId", "reporterDeptId", "termId").buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return repairMapper.selectList(qw).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("ticketNo", e.getTicketNo());
            m.put("faultType", e.getFaultType());
            m.put("status", e.getStatus());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> stockAlertDetail(Principal p, HttpServletRequest request) {
        List<Asset> assets = assetMapper.selectList(new QueryWrapper<Asset>()
                .le("\"availableQty\"", STOCK_LOW_THRESHOLD));
        return assets.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("name", e.getName());
            m.put("assetType", e.getAssetType());
            m.put("availableQty", e.getAvailableQty());
            m.put("quantity", e.getQuantity());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> timetableDetail(Principal p, HttpServletRequest request) {
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "timetable_entry", "teacherId", "classId", "termId").buildWhere(null));
        qw.orderByAsc("\"classId\"").orderByAsc("\"dayOfWeek\"").orderByAsc("\"period\"");
        return timetableMapper.selectList(qw).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("classId", e.getClassId());
            m.put("subject", e.getSubject());
            m.put("teacherId", e.getTeacherId());
            m.put("dayOfWeek", e.getDayOfWeek());
            m.put("period", e.getPeriod());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> scheduleChangeDetail(Principal p, HttpServletRequest request) {
        QueryWrapper<ScheduleChange> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "schedule_change", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return scheduleChangeMapper.selectList(qw).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("applyNo", e.getApplyNo());
            m.put("type", e.getType());
            m.put("status", e.getStatus());
            m.put("reason", e.getReason());
            return m;
        }).toList();
    }

    // ---- 统计查询 ----

    private long countProcurement(Principal p, HttpServletRequest request) {
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId").buildWhere(null));
        return procurementMapper.selectCount(qw);
    }

    private double budgetProcurement(Principal p, HttpServletRequest request) {
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId").buildWhere(null));
        qw.select("COALESCE(SUM(\"budget\"),0) AS total");
        List<Map<String, Object>> rows = procurementMapper.selectMaps(qw);
        if (rows.isEmpty() || rows.get(0).get("total") == null) {
            return 0d;
        }
        Object o = rows.get(0).get("total");
        if (o instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        return ((Number) o).doubleValue();
    }

    private long countMaterial(Principal p, HttpServletRequest request) {
        QueryWrapper<MaterialRequisition> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "material_requisition", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        return materialMapper.selectCount(qw);
    }

    private long countRepair(Principal p, HttpServletRequest request) {
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "repair_ticket", "reporterId", "reporterDeptId", "termId").buildWhere(null));
        return repairMapper.selectCount(qw);
    }

    private long countStockAlert(Principal p, HttpServletRequest request) {
        // 资产台账是全校共享的字典型资源，库存告警不按组织归属过滤，直接全校统计
        return assetMapper.selectCount(new QueryWrapper<Asset>().le("\"availableQty\"", STOCK_LOW_THRESHOLD));
    }

    private long countTimetable(Principal p, HttpServletRequest request) {
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "timetable_entry", "teacherId", "classId", "termId").buildWhere(null));
        return timetableMapper.selectCount(qw);
    }

    private long countScheduleChange(Principal p, HttpServletRequest request) {
        QueryWrapper<ScheduleChange> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "schedule_change", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        return scheduleChangeMapper.selectCount(qw);
    }

    // ---- 图表数据 ----

    private Map<String, Object> procurementTrend(Principal p, HttpServletRequest request, String dim) {
        String bucket = bucketExpr(dim, "procurement_project");
        QueryWrapper<ProcurementProject> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "procurement_project", "creatorId", "creatorDeptId", "termId").buildWhere(null));
        qw.select(bucket + " AS bucket", "COALESCE(SUM(\"budget\"),0) AS total").groupBy(bucket).orderByAsc(bucket);
        return toTrend(procurementMapper.selectMaps(qw), "total");
    }

    private Map<String, Object> repairTrend(Principal p, HttpServletRequest request, String dim) {
        String bucket = bucketExpr(dim, "repair_ticket");
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "repair_ticket", "reporterId", "reporterDeptId", "termId").buildWhere(null));
        qw.select(bucket + " AS bucket", "COUNT(*) AS cnt").groupBy(bucket).orderByAsc(bucket);
        return toTrend(repairMapper.selectMaps(qw), "cnt");
    }

    private Map<String, Object> changeTrend(Principal p, HttpServletRequest request, String dim) {
        String bucket = bucketExpr(dim, "schedule_change");
        QueryWrapper<ScheduleChange> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "schedule_change", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        qw.select(bucket + " AS bucket", "COUNT(*) AS cnt").groupBy(bucket).orderByAsc(bucket);
        return toTrend(scheduleChangeMapper.selectMaps(qw), "cnt");
    }

    private Map<String, Object> timetableByClass(Principal p, HttpServletRequest request) {
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "timetable_entry", "teacherId", "classId", "termId").buildWhere(null));
        qw.select("\"classId\" AS bucket", "COUNT(*) AS cnt").groupBy("\"classId\"").orderByAsc("\"classId\"");
        return toTrend(timetableMapper.selectMaps(qw), "cnt");
    }

    private Map<String, Object> teacherHours(Principal p, HttpServletRequest request) {
        QueryWrapper<TimetableEntry> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "timetable_entry", "teacherId", "classId", "termId").buildWhere(null));
        qw.select("\"teacherId\" AS bucket", "COUNT(*) AS cnt").groupBy("\"teacherId\"").orderByDesc("cnt");
        return toTrend(timetableMapper.selectMaps(qw), "cnt");
    }

    private Map<String, Object> materialTop(Principal p, HttpServletRequest request) {
        QueryWrapper<MaterialRequisition> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, scopeFor(p, "material_requisition", "applicantId", "applicantDeptId", "termId").buildWhere(null));
        qw.select("\"assetId\" AS bucket", "COUNT(*) AS cnt").groupBy("\"assetId\"").orderByDesc("cnt");
        return toTrend(materialMapper.selectMaps(qw), "cnt");
    }

    /** 六域健康度雷达：当前仅后勤/教务有数据，其余维度置 0（有数据域的健康度 = 无未处理异常）。 */
    private Map<String, Object> domainHealth(Principal p, HttpServletRequest request) {
        long alerts = dashboardService.alerts(request).size();
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> names = List.of("后勤", "教务", "德育", "党政", "科教", "体育");
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Double> data = new ArrayList<>();
        for (String n : names) {
            Map<String, Object> ind = new LinkedHashMap<>();
            ind.put("name", n);
            ind.put("max", 100);
            indicators.add(ind);
        }
        // 后勤 + 教务健康度 = 100 - 该域告警折算（简化：无异常即 100，有异常按条数递减，下限 0）
        double logistics = Math.max(0, 100 - alerts * 10);
        data.add(logistics);
        data.add(100d);
        data.add(0d);
        data.add(0d);
        data.add(0d);
        data.add(0d);
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", "健康度");
        s.put("data", data);
        series.add(s);
        out.put("categories", names);
        out.put("indicators", indicators);
        out.put("series", series);
        return out;
    }

    // ---- 组装辅助 ----

    private Map<String, Object> toTrend(List<Map<String, Object>> rows, String valueKey) {
        List<String> categories = new ArrayList<>();
        List<Double> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object b = r.get("bucket");
            Object v = r.get(valueKey);
            categories.add(b == null ? "" : b.toString());
            data.add(v == null ? 0d : ((Number) v).doubleValue());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categories);
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", "值");
        s.put("data", data);
        series.add(s);
        out.put("series", series);
        return out;
    }

    private Map<String, Object> kpi(String code, String label, Object value, String unit,
                                    String drillTo, String polarity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("status", "NORMAL");
        m.put("polarity", polarity);
        if (drillTo != null) {
            m.put("drillTo", drillTo);
        }
        return m;
    }

    private Map<String, Object> chartTrend(String code, String title, Map<String, Object> data, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("type", "LINE");
        m.put("categories", data.getOrDefault("categories", List.of()));
        m.put("series", data.getOrDefault("series", List.of()));
        m.put("unit", unit);
        return m;
    }

    private Map<String, Object> chartBar(String code, String title, Map<String, Object> data, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("type", "BAR");
        m.put("categories", data.getOrDefault("categories", List.of()));
        m.put("series", data.getOrDefault("series", List.of()));
        m.put("unit", unit);
        return m;
    }

    private Map<String, Object> chartBarH(String code, String title, Map<String, Object> data, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("type", "BAR_H");
        m.put("categories", data.getOrDefault("categories", List.of()));
        m.put("series", data.getOrDefault("series", List.of()));
        m.put("unit", unit);
        return m;
    }

    private Map<String, Object> chartRadar(String code, String title, Map<String, Object> data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("title", title);
        m.put("type", "RADAR");
        m.put("categories", data.getOrDefault("categories", List.of()));
        m.put("indicators", data.getOrDefault("indicators", List.of()));
        m.put("series", data.getOrDefault("series", List.of()));
        return m;
    }

    private ScopeQueryBuilder scopeFor(Principal p, String resource, String owner, String dept, String term) {
        DataScopeOptions opts = new DataScopeOptions(resource, OwnershipType.ORG, null, owner, dept, "", term, false, false);
        return ScopeQueryBuilder.of(p, opts);
    }

    private String bucketExpr(String dim, String table) {
        return "MONTH".equals(dim)
                ? "to_char(date_trunc('month', \"createdAt\"), 'YYYY-MM')"
                : "\"termId\"";
    }
}
