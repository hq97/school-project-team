package com.school.repair.dto;

/**
 * 修改报修场地（space）入参。
 */
public record SpaceUpdateRequest(
        String spaceName,
        String spaceType,
        Integer capacity,
        String status,
        String openScope) {
}
