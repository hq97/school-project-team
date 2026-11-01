package com.school.system.dto;

public record MenuUpdateRequest(
        String name,
        String path,
        String parentId,
        Integer sort,
        String component,
        String icon,
        Boolean listLike) {
}
