package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/** 工单状态更新入参（ASSIGNED → PROCESSING → COMPLETED / CANCELLED）。 */
public record RepairTicketStatusRequest(
        @NotBlank(message = "目标状态不能为空") String status) {
}
