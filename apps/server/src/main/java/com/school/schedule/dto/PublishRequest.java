package com.school.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 课表发布入参（M0604）。baseVersion 为乐观锁基线：落后于库内最新版本时 409 提示刷新。
 */
public record PublishRequest(
        @NotBlank(message = "学期不能为空") String termId,
        @NotNull(message = "生效周次不能为空") Integer effectiveWeek,
        Integer baseVersion) {
}
