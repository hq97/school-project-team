package com.school.system.dto;

import com.school.common.web.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录/当前用户响应结构单测（契约字段名）。
 */
class AuthResponseTest {

    private static AuthProfile profile() {
        return new AuthProfile("admin", "系统管理员",
                List.of("R-ADMIN"),
                List.of("BTN-SYS-USER-ADD"),
                List.of());
    }

    @Test
    void loginData_containsTokenAndProfileFields() {
        Map<String, Object> data = AuthResponse.loginData("tok123", profile());
        assertEquals("tok123", data.get("token"));
        assertEquals("admin", data.get("username"));
        assertEquals("系统管理员", data.get("realName"));
        assertEquals(List.of("R-ADMIN"), data.get("roles"));
        assertEquals(List.of("BTN-SYS-USER-ADD"), data.get("buttons"));
        assertTrue(data.containsKey("menus"));
    }

    @Test
    void meData_doesNotContainToken() {
        Map<String, Object> data = AuthResponse.meData(profile());
        assertFalse(data.containsKey("token"));
        assertEquals("admin", data.get("username"));
    }

    @Test
    void apiResponse_wrapsSuccess() {
        Map<String, Object> resp = ApiResponse.ok(Map.of("a", 1));
        assertEquals(Boolean.TRUE, resp.get("success"));
        assertEquals(Map.of("a", 1), resp.get("data"));
    }
}
