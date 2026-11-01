package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色-菜单关联（V1 表 role_menu，复合主键 roleId+menuId）。
 */
@TableName("\"role_menu\"")
public class RoleMenu {

    @TableField("\"roleId\"")
    private String roleId;

    @TableField("\"menuId\"")
    private String menuId;

    public RoleMenu() {
    }

    public RoleMenu(String roleId, String menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getMenuId() { return menuId; }
    public void setMenuId(String menuId) { this.menuId = menuId; }
}
