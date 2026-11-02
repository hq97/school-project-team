package com.school.workflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 审批动作请求（docs/07 AC-D3-5/7）。
 * action = APPROVE（通过）/ REJECT（驳回）；驳回建议填 comment（原因）。
 */
public record FlowTaskApproveRequest(
        @NotBlank(message = "审批动作不能为空") String action,
        String comment) {
}
