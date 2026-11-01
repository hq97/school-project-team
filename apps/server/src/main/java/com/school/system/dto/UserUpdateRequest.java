package com.school.system.dto;

import com.school.system.enums.AccountStatus;
import com.school.system.enums.StudentStatus;

public record UserUpdateRequest(
        String realName,
        AccountStatus accountStatus,
        StudentStatus studentStatus,
        String studentId) {
}
