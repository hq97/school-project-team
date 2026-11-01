package com.school.system.dto;

import jakarta.validation.constraints.NotBlank;

public record DictCreateRequest(
        @NotBlank(message = "字典编码不能为空") String dictCode,
        @NotBlank(message = "字典项编码不能为空") String itemCode,
        @NotBlank(message = "字典项名称不能为空") String itemName,
        Integer sort,
        Boolean enabled,
        Boolean isCurrent) {
}
