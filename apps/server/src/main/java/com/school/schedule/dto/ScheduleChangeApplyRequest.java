package com.school.schedule.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 调课/代课/请假代课申请入参（M0801）。
 *
 * <p>{@code type}：CHANGE（调课，须 targetDayOfWeek/targetPeriod）| SUBSTITUTE（代课，须 substituteTeacherId）
 * | LEAVE_SUBSTITUTE（请假代课，须 substituteTeacherId）。applicantId/termId 由服务端注入。</p>
 */
public record ScheduleChangeApplyRequest(
        @NotBlank(message = "类型不能为空") String type,
        @NotBlank(message = "课表条目不能为空") String entryId,
        Integer targetDayOfWeek,
        Integer targetPeriod,
        String substituteTeacherId,
        String reason) {
}
