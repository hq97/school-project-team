package com.school.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 教师任课表（V14 表 teacher_course）。docs/11 REQ-F1。
 *
 * <p>{@code teacherId} 归属教师；{@code classId} 任教班级；{@code subject} 学科文本；
 * {@code termId} 学期。唯一索引 (teacherId, classId, subject, termId) 防重复任课。</p>
 */
@TableName("\"teacher_course\"")
public class TeacherCourse {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"teacherId\"")
    private String teacherId;

    @TableField("\"classId\"")
    private String classId;

    private String subject;

    @TableField("\"termId\"")
    private String termId;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    @TableField("\"updatedAt\"")
    private OffsetDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
