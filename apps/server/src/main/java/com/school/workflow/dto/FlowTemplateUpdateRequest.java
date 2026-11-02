package com.school.workflow.dto;

import java.util.List;

/**
 * 流程模板更新请求。nodes 非空时全量替换节点列表（增删节点、调顺序）。
 */
public record FlowTemplateUpdateRequest(
        String name,
        String rejectPolicy,
        Boolean enabled,
        List<FlowNodeDto> nodes) {
}
