package com.school.system.dto;

import com.school.system.enums.AccountStatus;
import com.school.system.enums.StudentStatus;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "姓名不能为空") String realName,
        AccountStatus accountStatus,
        StudentStatus studentStatus,
        String studentId,
        List<String> roleIds) {
}
