package com.school.system.dto;

import com.school.system.enums.DeptType;

public record DeptUpdateRequest(
        String name,
        DeptType deptType,
        String parentId,
        Integer sort) {
}
