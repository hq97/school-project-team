package com.school.common.security;

import com.school.common.scope.OwnershipResolver;
import com.school.common.scope.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 第一层：JWT 认证过滤器（TS 语义来源：global-auth.guard.ts）。
 *
 * <p>解析 Bearer token → 实时查库账号状态（停用拒 401）→ 组装 {@link Principal} → 放入 SecurityContext。
 * 实时取角色，不做授权快照缓存（ADR-05）。</p>
 *
 * <p>失败/无 token 时<b>不设置认证</b>，交给 {@link SecurityConfig} 的
 * {@code .anyRequest().authenticated()} + EntryPoint 统一返回 401（fail-closed）。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final OwnershipResolver ownershipResolver;

    public JwtAuthenticationFilter(JwtService jwtService, OwnershipResolver ownershipResolver) {
        this.jwtService = jwtService;
        this.ownershipResolver = ownershipResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute("traceId", newTraceId());

        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String userId = jwtService.parseUserId(token);
            // 账号状态实时校验（停用账号持旧 token 一律 401，docs/04 §六场景 8）
            if (!ownershipResolver.isActive(userId)) {
                chain.doFilter(request, response);
                return;
            }
            Principal principal = ownershipResolver.resolve(userId);

            List<SimpleGrantedAuthority> authorities = principal.roleCodes().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            // 解析失败：清空上下文，让 EntryPoint 401
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String newTraceId() {
        return "t-" + Long.toString(System.currentTimeMillis(), 36) + "-"
                + Integer.toString((int) (Math.random() * 0xFFFFFF), 36);
    }
}
