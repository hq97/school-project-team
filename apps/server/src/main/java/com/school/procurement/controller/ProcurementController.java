package com.school.procurement.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.procurement.dto.ProcurementCreateRequest;
import com.school.procurement.service.ProcurementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 采购看板-项目管理（M0202）。数据范围 ORG（creatorId / creatorDeptId / termId）。
 */
@RestController
@RequestMapping("/api/procurement/projects")
public class ProcurementController {

    private final ProcurementService procurementService;

    public ProcurementController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId", write = true)
    @RequireButton("BTN-PUR-NEW")
    @RequireMenu("M0202")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ProcurementCreateRequest req) {
        return ApiResponse.ok(procurementService.create(req));
    }

    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId")
    @RequireMenu("M0202")
    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        return ApiResponse.ok(procurementService.list(request));
    }

    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId")
    @RequireMenu("M0202")
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(procurementService.detail(request, id));
    }

    /** 我的采购：接口恒本人，不随角色数据范围放大（M0103 我的申请）。 */
    @DataScope(resource = "procurement_project", ownership = OwnershipType.ORG,
            ownerField = "creatorId", deptField = "creatorDeptId", termField = "termId", forceSelf = true)
    @RequireMenu("M0103")
    @GetMapping("/my")
    public Map<String, Object> my(HttpServletRequest request) {
        return ApiResponse.ok(procurementService.my(request));
    }
}
