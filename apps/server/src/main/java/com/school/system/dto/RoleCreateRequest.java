package com.school.system.dto;

import com.school.common.scope.ScopeLevel;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleCreateRequest(
        @NotBlank(message = "角色编码不能为空") String code,
        @NotBlank(message = "角色名不能为空") String name,
        ScopeLevel dataScope,
        Boolean scopeComplete,
        List<String> menuIds,
        List<String> buttonIds,
        List<ScopeItem> scopes) {
}
