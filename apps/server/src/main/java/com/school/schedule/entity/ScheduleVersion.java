package com.school.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 课表版本（V1 表 schedule_version）。docs/11 REQ-S3。
 */
@TableName("\"schedule_version\"")
public class ScheduleVersion {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"termId\"")
    private String termId;

    @TableField("\"effectiveWeek\"")
    private Integer effectiveWeek;

    private Integer version;

    private String status;

    @TableField("\"publishedAt\"")
    private OffsetDateTime publishedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public Integer getEffectiveWeek() { return effectiveWeek; }
    public void setEffectiveWeek(Integer effectiveWeek) { this.effectiveWeek = effectiveWeek; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
