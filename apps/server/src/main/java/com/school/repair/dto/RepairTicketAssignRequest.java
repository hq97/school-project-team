package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/** 分派维修人入参（维修人 = R-REPAIRMAN 角色下的用户 id）。 */
public record RepairTicketAssignRequest(
        @NotBlank(message = "维修人不能为空") String repairmanId) {
}
