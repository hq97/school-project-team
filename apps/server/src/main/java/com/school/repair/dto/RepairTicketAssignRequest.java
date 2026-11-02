package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/** 分派维修人入参。 */
public record RepairTicketAssignRequest(
        @NotBlank(message = "维修人不能为空") String assignedTo) {
}
