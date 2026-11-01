package com.school.system.dto;

import com.school.common.scope.ScopeLevel;

public record RoleUpdateRequest(
        String name,
        ScopeLevel dataScope,
        Boolean scopeComplete) {
}
