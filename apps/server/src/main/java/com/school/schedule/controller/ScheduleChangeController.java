package com.school.schedule.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.schedule.dto.ScheduleChangeApplyRequest;
import com.school.schedule.service.ScheduleChangeService;
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
 * 调课/代课/请假代课（M08）。
 *
 * <p>申请 SELF（防越权改他人课）；我的调课 forceSelf；详情 matchRecord 判归属（R-TEACHER 仅本人、
 * R-DEAN 全校）。审批走 {@code /api/flow/todos}（businessType=schedule_change）。</p>
 */
@RestController
@RequestMapping("/api/schedule-change")
public class ScheduleChangeController {

    private final ScheduleChangeService changeService;

    public ScheduleChangeController(ScheduleChangeService changeService) {
        this.changeService = changeService;
    }

    @DataScope(resource = "schedule_change", ownership = OwnershipType.ORG,
            ownerField = "applicantId", termField = "termId")
    @RequireButton("BTN-SCH-CHANGE-APPLY")
    @RequireMenu("M0801")
    @PostMapping("/apply")
    public Map<String, Object> apply(@Valid @RequestBody ScheduleChangeApplyRequest req) {
        return ApiResponse.ok(changeService.apply(req));
    }

    @DataScope(resource = "schedule_change", ownership = OwnershipType.ORG,
            ownerField = "applicantId", termField = "termId", forceSelf = true)
    @RequireMenu("M0802")
    @GetMapping("/my")
    public Map<String, Object> my(HttpServletRequest request) {
        return ApiResponse.ok(changeService.my(request));
    }

    @DataScope(resource = "schedule_change", ownership = OwnershipType.ORG,
            ownerField = "applicantId", termField = "termId")
    @RequireMenu("M0802")
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(changeService.detail(request, id));
    }
}
