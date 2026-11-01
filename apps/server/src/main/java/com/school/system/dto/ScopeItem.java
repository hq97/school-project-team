package com.school.system.dto;

import com.school.common.scope.ScopeLevel;

/**
 * 数据对象范围授权项（role_object_scope）：对象键 + 归属类型 + 数据范围。
 * {@code ownership} 用英文码（ORG/RELATION/ROLE_GRANT），服务端转 {@code OwnershipType}。
 */
public record ScopeItem(String objectKey, String ownership, ScopeLevel dataScope) {
}
