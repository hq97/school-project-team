package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.scope.OwnershipType;
import com.school.common.scope.ScopeLevel;

/**
 * 角色-数据对象范围授权（V1 表 role_object_scope）。
 *
 * <p>一个角色可对多个业务对象（objectKey）分别授权归属类型 + 数据范围。
 * {@code ownership} 库内为 OwnershipType（ORG/RELATION/ROLE_GRANT），{@code dataScope} 为 DataScope（SELF/GRADE/ALL）。</p>
 */
@TableName("\"role_object_scope\"")
public class RoleObjectScope {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"roleId\"")
    private String roleId;

    @TableField("\"objectKey\"")
    private String objectKey;

    private OwnershipType ownership;

    @TableField("\"dataScope\"")
    private ScopeLevel dataScope;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public OwnershipType getOwnership() { return ownership; }
    public void setOwnership(OwnershipType ownership) { this.ownership = ownership; }
    public ScopeLevel getDataScope() { return dataScope; }
    public void setDataScope(ScopeLevel dataScope) { this.dataScope = dataScope; }
}
