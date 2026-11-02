package com.school.repair.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增报修场地（space）入参。status 由服务端固定 AVAILABLE，openScope 缺省 ALL。
 */
public record SpaceCreateRequest(
        @NotBlank(message = "场地名称不能为空") String spaceName,
        @NotBlank(message = "场地类型不能为空") String spaceType,
        Integer capacity,
        String openScope) {
}
