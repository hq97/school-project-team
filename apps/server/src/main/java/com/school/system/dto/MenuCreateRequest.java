package com.school.system.dto;

import jakarta.validation.constraints.NotBlank;

public record MenuCreateRequest(
        @NotBlank(message = "菜单编码不能为空") String code,
        @NotBlank(message = "菜单名不能为空") String name,
        String path,
        String parentId,
        Integer sort,
        String component,
        String icon,
        Boolean listLike) {
}
