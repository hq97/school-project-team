package com.school.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 权限三层 fail-closed 第一层：Spring Security 过滤器链。
 *
 * <ul>
 *   <li>默认 {@code .anyRequest().authenticated()} 拒绝（fail-closed），只放行登录 / 字典只读 / 健康检查；</li>
 *   <li>stateless（无 Session），认证由 {@link JwtAuthenticationFilter} 完成；</li>
 *   <li>未认证 → EntryPoint 401；已认证但无权限 → AccessDeniedHandler 403。</li>
 * </ul>
 *
 * <p>菜单 40301 / 按钮 40302 / 数据范围 40303 的细粒度校验由 {@code MenuButtonInterceptor} /
 * {@code DataScopeInterceptor} 承担（见 com.school.common.scope.WebMvcConfig），不走 Spring Security 的授权注解。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 白名单：登录、字典只读消费、健康检查（对齐 ADR v3.0 白名单）
                        .requestMatchers("/api/auth/login", "/api/dicts/items/**", "/api/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        BizCode.UNAUTHENTICATED.code(), BizCode.UNAUTHENTICATED.message()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, 403, "无权限")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeJson(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
