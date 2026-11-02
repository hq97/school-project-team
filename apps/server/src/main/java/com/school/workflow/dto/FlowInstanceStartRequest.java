package com.school.workflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 流程实例启动请求（业务发起审批时调用）。发起人身份由服务端从登录态取，不由请求体传。
 */
public record FlowInstanceStartRequest(
        @NotBlank(message = "业务类型不能为空") String businessType,
        @NotBlank(message = "业务对象不能为空") String businessId) {
}
