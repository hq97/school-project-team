package com.school.common.scope;

/**
 * 归属类型（TS 语义来源：scope.types.ts；docs/04 §1.5/§1.6/§1.7）：
 *
 * <ul>
 *   <li>{@link #ORG}        组织型：部门树承载 SELF / GRADE / ALL（含 CLASS 班级载体、SUBJECT 教研组载体）</li>
 *   <li>{@link #RELATION}   关系型：家长-子女 student_parent</li>
 *   <li>{@link #ROLE_GRANT} 角色授权型：公告 notice（命中可见角色 + 菜单权限）</li>
 *   <li>{@link #PUBLIC}     全校共享/公共对象：不做记录级过滤，仅由菜单/按钮权限控制（如 asset 可借列表）</li>
 * </ul>
 */
public enum OwnershipType {
    ORG,
    RELATION,
    ROLE_GRANT,
    PUBLIC
}
