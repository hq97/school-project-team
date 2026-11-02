package com.school.dashboard.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.dashboard.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 校长端聚合统计（M0201 采购总览 / M0101 异常清单 / M0204 趋势）。
 *
 * <p>范围语义：R-PRINCIPAL/R-LOGISTICS（ALL）看全校，R-GRADE（GRADE）看本年级。
 * 聚合在服务层按各表归属字段经 ScopeQueryBuilder 注入。</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId")
    @RequireMenu("M0201")
    @GetMapping("/procurement/summary")
    public Map<String, Object> procurementSummary(HttpServletRequest request) {
        return ApiResponse.ok(dashboardService.procurementSummary(request));
    }

    @DataScope(resource = "alert", ownership = OwnershipType.ORG,
            ownerField = "ownerId", deptField = "ownerDeptId", termField = "termId")
    @RequireMenu("M0101")
    @GetMapping("/alerts")
    public Map<String, Object> alerts(HttpServletRequest request) {
        return ApiResponse.ok(dashboardService.alerts(request));
    }

    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId")
    @RequireMenu("M0204")
    @GetMapping("/trends")
    public Map<String, Object> trends(HttpServletRequest request,
                                      @RequestParam(defaultValue = "TERM") String dim) {
        return ApiResponse.ok(dashboardService.trends(request, dim));
    }
}
