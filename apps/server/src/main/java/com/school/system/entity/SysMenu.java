package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统菜单（V1 表 sys_menu + V4 新增 component/icon）。
 */
@TableName("\"sys_menu\"")
public class SysMenu {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    private String path;

    @TableField("\"parentId\"")
    private String parentId;

    private Integer sort;

    @TableField("\"listLike\"")
    private Boolean listLike;

    private String component;

    private String icon;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Boolean getListLike() { return listLike; }
    public void setListLike(Boolean listLike) { this.listLike = listLike; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
