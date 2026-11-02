package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增报修故障类型入参。itemCode 为英文码（DICT-FAULT-TYPE 项），去重校验。
 */
public record FaultTypeCreateRequest(
        @NotBlank(message = "故障类型编码不能为空") String itemCode,
        @NotBlank(message = "故障类型名称不能为空") String itemName,
        Integer sort) {
}
