package com.school.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 课表条目（V1 表 timetable_entry）。docs/11 REQ-S3。
 *
 * <p>{@code subject} 为学科文本；{@code substituteTeacherId} 代课教师（SUBSTITUTE / LEAVE_SUBSTITUTE 生效）。</p>
 */
@TableName("\"timetable_entry\"")
public class TimetableEntry {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"versionId\"")
    private String versionId;

    @TableField("\"spaceId\"")
    private String spaceId;

    @TableField("\"teacherId\"")
    private String teacherId;

    @TableField("\"classId\"")
    private String classId;

    private String subject;

    @TableField("\"termId\"")
    private String termId;

    private Integer week;

    @TableField("\"dayOfWeek\"")
    private Integer dayOfWeek;

    private Integer period;

    @TableField("\"substituteTeacherId\"")
    private String substituteTeacherId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public Integer getWeek() { return week; }
    public void setWeek(Integer week) { this.week = week; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }
    public String getSubstituteTeacherId() { return substituteTeacherId; }
    public void setSubstituteTeacherId(String substituteTeacherId) { this.substituteTeacherId = substituteTeacherId; }
}
