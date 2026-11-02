package com.school.repair.dto;

/**
 * 修改报修故障类型入参（名称 / 排序 / 启停用）。
 */
public record FaultTypeUpdateRequest(
        String itemName,
        Integer sort,
        Boolean enabled) {
}
