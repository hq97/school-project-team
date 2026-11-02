package com.school.procurement.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 发起采购需求入参。creatorId/creatorDeptId/termId 由服务端从登录态注入，不接受前端传入。
 */
public record ProcurementCreateRequest(
        @NotBlank(message = "采购项目名称不能为空") String name,
        BigDecimal budget) {
}
