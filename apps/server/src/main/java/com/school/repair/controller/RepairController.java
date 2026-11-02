package com.school.repair.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.repair.dto.RepairTicketAssignRequest;
import com.school.repair.dto.RepairTicketCreateRequest;
import com.school.repair.dto.RepairTicketStatusRequest;
import com.school.repair.service.RepairService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报修预约（M0401 提交 / M0403 工单处理）。数据范围 ORG（reporterId / reporterDeptId / termId）。
 */
@RestController
@RequestMapping("/api/repair")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @RequireMenu("M0401")
    @GetMapping("/spaces")
    public Map<String, Object> spaces() {
        return ApiResponse.ok(repairService.spaces());
    }

    @DataScope(resource = "repair_ticket", ownership = OwnershipType.ORG,
            ownerField = "reporterId", deptField = "reporterDeptId", termField = "termId", write = true)
    @RequireButton("BTN-REP-SUBMIT")
    @RequireMenu("M0401")
    @PostMapping("/tickets")
    public Map<String, Object> submit(@Valid @RequestBody RepairTicketCreateRequest req) {
        return ApiResponse.ok(repairService.submit(req));
    }

    @DataScope(resource = "repair_ticket", ownership = OwnershipType.ORG,
            ownerField = "reporterId", deptField = "reporterDeptId", termField = "termId", forceSelf = true)
    @RequireMenu("M0402")
    @GetMapping("/tickets/my")
    public Map<String, Object> my(HttpServletRequest request) {
        return ApiResponse.ok(repairService.my(request));
    }

    @DataScope(resource = "repair_ticket", ownership = OwnershipType.ORG,
            ownerField = "reporterId", deptField = "reporterDeptId", termField = "termId")
    @RequireMenu("M0403")
    @GetMapping("/tickets")
    public Map<String, Object> list(HttpServletRequest request,
                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(repairService.list(request, status));
    }

    @DataScope(resource = "repair_ticket", ownership = OwnershipType.ORG,
            ownerField = "reporterId", deptField = "reporterDeptId", termField = "termId")
    @RequireButton("BTN-REP-ASSIGN")
    @RequireMenu("M0403")
    @PutMapping("/tickets/{id}/assign")
    public Map<String, Object> assign(@PathVariable String id,
                                      @Valid @RequestBody RepairTicketAssignRequest req,
                                      HttpServletRequest request) {
        return ApiResponse.ok(repairService.assign(request, id, req));
    }

    @DataScope(resource = "repair_ticket", ownership = OwnershipType.ORG,
            ownerField = "reporterId", deptField = "reporterDeptId", termField = "termId")
    @RequireButton("BTN-REP-STATUS")
    @RequireMenu("M0403")
    @PutMapping("/tickets/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable String id,
                                            @Valid @RequestBody RepairTicketStatusRequest req,
                                            HttpServletRequest request) {
        return ApiResponse.ok(repairService.updateStatus(request, id, req));
    }
}
