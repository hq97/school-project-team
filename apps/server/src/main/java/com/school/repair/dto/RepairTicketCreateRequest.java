package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交报修入参。reporterId/reporterDeptId/termId 由服务端从登录态注入，不接受前端传入。
 */
public record RepairTicketCreateRequest(
        String spaceId,
        @NotBlank(message = "故障类型不能为空") String faultType) {
}
