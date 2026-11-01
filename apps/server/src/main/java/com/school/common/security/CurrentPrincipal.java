package com.school.common.security;

import com.school.common.scope.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring SecurityContext 取当前 {@link Principal} 的静态工具。
 *
 * <p>Principal 由 {@link JwtAuthenticationFilter} 解析 JWT 后放入 {@code SecurityContext}，
 * 后续的 {@code MenuButtonInterceptor} / {@code DataScopeInterceptor} / Controller 均通过本工具获取。</p>
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    /** 未认证返回 null。 */
    public static Principal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object p = auth.getPrincipal();
        if (p instanceof Principal principal) {
            return principal;
        }
        return null;
    }

    /** 未认证抛 401（防御性兜底：正常情况下 Security 过滤器链已拦截未认证请求）。 */
    public static Principal require() {
        Principal p = get();
        if (p == null) {
            throw BizException.unauthorized();
        }
        return p;
    }
}
