package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.scope.ScopeLevel;

import java.time.OffsetDateTime;

/**
 * 系统角色（V1 表 sys_role）。
 *
 * <p>{@code dataScope} 库内为 DataScope 枚举（SELF/GRADE/ALL），与权限骨架的 {@link ScopeLevel} 同名同值，
 * 直接复用 {@link ScopeLevel}，不另起一套枚举。</p>
 */
@TableName("\"sys_role\"")
public class SysRole {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    @TableField("\"dataScope\"")
    private ScopeLevel dataScope;

    @TableField("\"scopeComplete\"")
    private Boolean scopeComplete;

    private Boolean builtin;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ScopeLevel getDataScope() { return dataScope; }
    public void setDataScope(ScopeLevel dataScope) { this.dataScope = dataScope; }
    public Boolean getScopeComplete() { return scopeComplete; }
    public void setScopeComplete(Boolean scopeComplete) { this.scopeComplete = scopeComplete; }
    public Boolean getBuiltin() { return builtin; }
    public void setBuiltin(Boolean builtin) { this.builtin = builtin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
