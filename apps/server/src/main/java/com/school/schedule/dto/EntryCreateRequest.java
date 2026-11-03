package com.school.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 手动排课入参（M0602）。termId 由服务端按登录态当前学期兜底，前端可选传。
 */
public record EntryCreateRequest(
        @NotBlank(message = "班级不能为空") String classId,
        @NotBlank(message = "学科不能为空") String subject,
        @NotBlank(message = "教师不能为空") String teacherId,
        String spaceId,
        String termId,
        @NotNull(message = "周次不能为空") Integer week,
        @NotNull(message = "星期不能为空") Integer dayOfWeek,
        @NotNull(message = "节次不能为空") Integer period) {
}
