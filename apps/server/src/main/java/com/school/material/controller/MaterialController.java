package com.school.material.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.material.dto.MaterialRequisitionRequest;
import com.school.material.service.MaterialService;
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
 * 物资领用（M0301 领用申请）。数据范围 ORG（applicantId / applicantDeptId / termId）。
 */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** 可领用资产列表：PUBLIC（全校可借），仅菜单控制。 */
    @DataScope(resource = "asset", ownership = OwnershipType.PUBLIC)
    @RequireMenu("M0301")
    @GetMapping("/assets")
    public Map<String, Object> assets() {
        return ApiResponse.ok(materialService.assets());
    }

    @DataScope(resource = "material_requisition", ownership = OwnershipType.ORG,
            ownerField = "applicantId", deptField = "applicantDeptId", termField = "termId", write = true)
    @RequireButton("BTN-MAT-APPLY")
    @RequireMenu("M0301")
    @PostMapping("/requisitions")
    public Map<String, Object> submit(@Valid @RequestBody MaterialRequisitionRequest req) {
        return ApiResponse.ok(materialService.submit(req));
    }

    /** 我的申请（forceSelf，M0302 我的申请）。 */
    @DataScope(resource = "material_requisition", ownership = OwnershipType.ORG,
            ownerField = "applicantId", deptField = "applicantDeptId", termField = "termId", forceSelf = true)
    @RequireMenu("M0302")
    @GetMapping("/requisitions/my")
    public Map<String, Object> my(HttpServletRequest request) {
        return ApiResponse.ok(materialService.my(request));
    }

    /** 领用详情（matchRecord 判归属，M0302 我的申请）。 */
    @DataScope(resource = "material_requisition", ownership = OwnershipType.ORG,
            ownerField = "applicantId", deptField = "applicantDeptId", termField = "termId")
    @RequireMenu("M0302")
    @GetMapping("/requisitions/{id}")
    public Map<String, Object> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(materialService.detail(request, id));
    }
}
