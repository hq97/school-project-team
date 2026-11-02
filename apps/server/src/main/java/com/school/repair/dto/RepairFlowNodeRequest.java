package com.school.repair.dto;

/**
 * 报修审批流程节点输入（列表顺序即流转顺序）。
 */
public record RepairFlowNodeRequest(
        String name,
        String approveRole,
        Integer timeoutHours) {
}
