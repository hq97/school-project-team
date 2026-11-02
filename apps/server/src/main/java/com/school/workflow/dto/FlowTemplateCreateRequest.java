package com.school.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 流程模板创建请求（docs/07 AC-D3-1）。节点列表有序，顺序即流转顺序。
 */
public record FlowTemplateCreateRequest(
        @NotBlank(message = "模板编码不能为空") String code,
        @NotBlank(message = "模板名称不能为空") String name,
        @NotBlank(message = "业务类型不能为空") String businessType,
        String rejectPolicy,
        List<FlowNodeDto> nodes) {
}
