package com.school.system.dto;

import java.util.List;

public record RoleMenuAssignRequest(
        List<String> menuIds,
        List<String> buttonIds) {
}
