package com.school.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 场地占用（V1 表 space_occupancy）。docs/04 §1.11。
 *
 * <p>报修配置删除场地时用它做引用计数（被占用记录引用时 409），故仅需 {@code spaceId} 关联字段；
 * 其余列按表结构补齐，便于未来复用。</p>
 */
@TableName("\"space_occupancy\"")
public class SpaceOccupancy {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"spaceId\"")
    private String spaceId;

    @TableField("\"termId\"")
    private String termId;

    private Integer week;

    @TableField("\"dayOfWeek\"")
    private Integer dayOfWeek;

    private Integer period;

    private String source;

    @TableField("\"bizRefId\"")
    private String bizRefId;

    private String status;

    @TableField("\"createdBy\"")
    private String createdBy;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public Integer getWeek() { return week; }
    public void setWeek(Integer week) { this.week = week; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getBizRefId() { return bizRefId; }
    public void setBizRefId(String bizRefId) { this.bizRefId = bizRefId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
