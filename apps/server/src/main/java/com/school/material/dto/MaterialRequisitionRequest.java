package com.school.material.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提交领用申请入参。applicantId/applicantDeptId/termId 由服务端从登录态注入，不接受前端传入。
 */
public record MaterialRequisitionRequest(
        @NotBlank(message = "资产不能为空") String assetId,
        @NotNull(message = "领用数量不能为空") @Min(value = 1, message = "领用数量必须大于 0") Integer quantity) {
}
