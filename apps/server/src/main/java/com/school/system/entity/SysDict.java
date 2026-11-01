package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 数据字典项（V1 表 sys_dict）。
 */
@TableName("\"sys_dict\"")
public class SysDict {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"dictCode\"")
    private String dictCode;

    @TableField("\"itemCode\"")
    private String itemCode;

    @TableField("\"itemName\"")
    private String itemName;

    private Integer sort;

    private Boolean enabled;

    @TableField("\"isCurrent\"")
    private Boolean isCurrent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}
