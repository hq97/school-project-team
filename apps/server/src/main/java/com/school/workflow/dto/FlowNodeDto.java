package com.school.workflow.dto;

/**
 * 流程节点输入（模板创建/更新时随模板一起提交，列表顺序即流转顺序）。
 */
public record FlowNodeDto(
        String name,
        String approveRole,
        Integer timeoutHours) {
}
