package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.school.system.enums.AccountStatus;
import com.school.system.enums.StudentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统用户（V1 表 sys_user）。
 *
 * <p>PostgreSQL 列名为 camelCase 带引号，MyBatis-Plus 生成的 SQL 需显式带引号列名，
 * 否则 PG 会把未加引号的标识符折叠为小写导致「列不存在」。</p>
 */
@TableName("\"sys_user\"")
public class SysUser {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String username;

    @TableField("\"passwordHash\"")
    @JsonIgnore
    private String passwordHash;

    @TableField("\"realName\"")
    private String realName;

    @TableField("\"accountStatus\"")
    private AccountStatus accountStatus;

    @TableField("\"studentStatus\"")
    private StudentStatus studentStatus;

    @TableField("\"studentId\"")
    private String studentId;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    /** 用户角色（列表查询时填充，非表字段）。每项含 id/code/name。 */
    @TableField(exist = false)
    private List<Map<String, Object>> roles;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public StudentStatus getStudentStatus() { return studentStatus; }
    public void setStudentStatus(StudentStatus studentStatus) { this.studentStatus = studentStatus; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<Map<String, Object>> getRoles() { return roles; }
    public void setRoles(List<Map<String, Object>> roles) { this.roles = roles; }
}
