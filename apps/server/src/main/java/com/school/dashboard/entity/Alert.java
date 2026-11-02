package com.school.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 异常提醒（V1 表 alert，C10 超期提醒统一落库）。dashboard 实时计算 + 读库双通道。
 */
@TableName("\"alert\"")
public class Alert {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"alertType\"")
    private String alertType;

    @TableField("\"bizType\"")
    private String bizType;

    @TableField("\"bizId\"")
    private String bizId;

    @TableField("\"ownerId\"")
    private String ownerId;

    @TableField("\"ownerDeptId\"")
    private String ownerDeptId;

    @TableField("\"termId\"")
    private String termId;

    private String level;

    private String status;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getOwnerDeptId() { return ownerDeptId; }
    public void setOwnerDeptId(String ownerDeptId) { this.ownerDeptId = ownerDeptId; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
