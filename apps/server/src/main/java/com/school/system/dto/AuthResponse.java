package com.school.system.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录/当前用户响应体装配（纯逻辑，可单测）。
 *
 * <p>login 返回含 token 的完整档案；me 不含 token（token 已在请求头）。二者外层均为
 * {@code {success:true, data:{...}}}（由 {@code ApiResponse.ok} 统一包裹）。</p>
 */
public final class AuthResponse {

    private AuthResponse() {
    }

    public static Map<String, Object> loginData(String token, AuthProfile profile) {
        Map<String, Object> data = base(profile);
        data.put("token", token);
        return data;
    }

    public static Map<String, Object> meData(AuthProfile profile) {
        return base(profile);
    }

    private static Map<String, Object> base(AuthProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", profile.username());
        data.put("realName", profile.realName());
        data.put("roles", profile.roles());
        data.put("buttons", profile.buttons());
        data.put("menus", profile.menus());
        return data;
    }
}
