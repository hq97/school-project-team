package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色-按钮关联（V1 表 role_button，复合主键 roleId+buttonId）。
 */
@TableName("\"role_button\"")
public class RoleButton {

    @TableField("\"roleId\"")
    private String roleId;

    @TableField("\"buttonId\"")
    private String buttonId;

    public RoleButton() {
    }

    public RoleButton(String roleId, String buttonId) {
        this.roleId = roleId;
        this.buttonId = buttonId;
    }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getButtonId() { return buttonId; }
    public void setButtonId(String buttonId) { this.buttonId = buttonId; }
}
