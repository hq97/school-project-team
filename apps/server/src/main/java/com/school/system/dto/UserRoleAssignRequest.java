package com.school.system.dto;

import java.util.List;

public record UserRoleAssignRequest(List<String> roleIds) {
}
