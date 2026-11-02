package com.school.repair.dto;

import java.util.List;

/**
 * 报修审批流程更新入参：nodes 按数组顺序重建 FT-REPAIR 节点（seq 从 1 递增）。
 */
public record RepairFlowUpdateRequest(List<RepairFlowNodeRequest> nodes) {
}
