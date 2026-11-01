package com.school.system.dto;

import java.util.List;

/**
 * 登录/当前用户档案（登录返回的 data 中除 token 外的部分，/me 直接复用）。
 */
public record AuthProfile(
        String username,
        String realName,
        List<String> roles,
        List<String> buttons,
        List<MenuNode> menus) {
}
