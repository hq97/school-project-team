package com.school.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 教师任课表增改入参（REQ-F1）。termId 由服务端按登录态当前学期兜底，前端可选传。
 */
public record TeacherCourseRequest(
        @NotBlank(message = "教师不能为空") String teacherId,
        @NotBlank(message = "班级不能为空") String classId,
        @NotBlank(message = "学科不能为空") String subject,
        String termId) {
}
