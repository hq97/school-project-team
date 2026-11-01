package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统按钮权限点（V1 表 sys_button）。
 */
@TableName("\"sys_button\"")
public class SysButton {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    @TableField("\"menuId\"")
    private String menuId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMenuId() { return menuId; }
    public void setMenuId(String menuId) { this.menuId = menuId; }
}
