package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户-角色关联（V1 表 user_role，复合主键 userId+roleId）。
 */
@TableName("\"user_role\"")
public class UserRole {

    @TableField("\"userId\"")
    private String userId;

    @TableField("\"roleId\"")
    private String roleId;

    public UserRole() {
    }

    public UserRole(String userId, String roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
}
