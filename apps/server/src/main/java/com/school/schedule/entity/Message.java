package com.school.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 站内消息（V1 表 message）。docs/15 消息中心。
 *
 * <p>V1 表仅 {@code title} 无 {@code content} 列，首版直接写 message 表即可（不接 outbox worker）。</p>
 */
@TableName("\"message\"")
public class Message {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"receiverId\"")
    private String receiverId;

    private String category;

    private String title;

    @TableField("\"bizType\"")
    private String bizType;

    @TableField("\"bizId\"")
    private String bizId;

    @TableField("\"linkTo\"")
    private String linkTo;

    @TableField("\"read\"")
    private Boolean read;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getLinkTo() { return linkTo; }
    public void setLinkTo(String linkTo) { this.linkTo = linkTo; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
