package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.system.enums.DeptType;

/**
 * 系统部门（V1 表 sys_dept）。
 */
@TableName("\"sys_dept\"")
public class SysDept {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String name;

    @TableField("\"deptType\"")
    private DeptType deptType;

    @TableField("\"parentId\"")
    private String parentId;

    private String path;

    private Integer sort;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DeptType getDeptType() { return deptType; }
    public void setDeptType(DeptType deptType) { this.deptType = deptType; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
