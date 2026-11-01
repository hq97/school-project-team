package com.school.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一成功响应封装：{@code { success: true, data: <payload> }}。
 *
 * <p>对齐 vue-pure-admin 前端约定的登录/CRUD 成功体（team-lead 门禁4 第三步契约），
 * 与 {@code GlobalExceptionHandler} 的错误体 {@code {code,message,data,...}} 分离。</p>
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }
}
