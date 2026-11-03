package com.school.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 调课/代课/请假代课申请（V1 表 schedule_change + V13 扩展）。
 *
 * <p>{@code targetSlot} 为 JSON 字符串（V13 由 JSONB 改 TEXT，应用层 Jackson 解析），
 * 形如 {@code {"dayOfWeek":3,"period":4}}。{@code type}：CHANGE / SUBSTITUTE / LEAVE_SUBSTITUTE。</p>
 */
@TableName("\"schedule_change\"")
public class ScheduleChange {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"applyNo\"")
    private String applyNo;

    @TableField("\"applicantId\"")
    private String applicantId;

    @TableField("\"termId\"")
    private String termId;

    @TableField("\"targetSlot\"")
    private String targetSlot;

    private String reason;

    private String status;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    private String type;

    @TableField("\"entryId\"")
    private String entryId;

    @TableField("\"substituteTeacherId\"")
    private String substituteTeacherId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplyNo() { return applyNo; }
    public void setApplyNo(String applyNo) { this.applyNo = applyNo; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getTargetSlot() { return targetSlot; }
    public void setTargetSlot(String targetSlot) { this.targetSlot = targetSlot; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    public String getSubstituteTeacherId() { return substituteTeacherId; }
    public void setSubstituteTeacherId(String substituteTeacherId) { this.substituteTeacherId = substituteTeacherId; }
}
