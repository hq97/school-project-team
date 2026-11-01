package com.school.system.dto;

import com.school.system.enums.DeptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeptCreateRequest(
        @NotBlank(message = "部门名不能为空") String name,
        @NotNull(message = "部门类型不能为空") DeptType deptType,
        String parentId,
        Integer sort) {
}
