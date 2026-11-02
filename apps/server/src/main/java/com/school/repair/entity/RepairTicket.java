package com.school.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 报修工单（V1 表 repair_ticket）。走自有状态机，不走审批流。
 *
 * <p>状态机：PENDING(待分派) → ASSIGNED(已分派) → PROCESSING(处理中) → COMPLETED(已完成)，另有 CANCELLED。</p>
 */
@TableName("\"repair_ticket\"")
public class RepairTicket {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"ticketNo\"")
    private String ticketNo;

    @TableField("\"reporterId\"")
    private String reporterId;

    @TableField("\"reporterDeptId\"")
    private String reporterDeptId;

    @TableField("\"spaceId\"")
    private String spaceId;

    @TableField("\"faultType\"")
    private String faultType;

    @TableField("\"termId\"")
    private String termId;

    @TableField("\"description\"")
    private String description;

    private String status;

    @TableField("\"assignedTo\"")
    private String assignedTo;

    @TableField("\"assignedAt\"")
    private OffsetDateTime assignedAt;

    @TableField("\"completedAt\"")
    private OffsetDateTime completedAt;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }
    public String getReporterDeptId() { return reporterDeptId; }
    public void setReporterDeptId(String reporterDeptId) { this.reporterDeptId = reporterDeptId; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getFaultType() { return faultType; }
    public void setFaultType(String faultType) { this.faultType = faultType; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
