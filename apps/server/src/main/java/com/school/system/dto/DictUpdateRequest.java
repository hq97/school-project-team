package com.school.system.dto;

public record DictUpdateRequest(
        String itemName,
        Integer sort,
        Boolean enabled,
        Boolean isCurrent) {
}
