package com.school.common.security;

import com.school.common.scope.Principal;
import com.school.common.scope.PublicDataScope;
import com.school.common.web.ApiResponse;
import com.school.system.dto.AuthProfile;
import com.school.system.dto.AuthResponse;
import com.school.system.service.AuthProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口（白名单，无需认证）。
 *
 * <p>登录校验用户名密码签发 JWT，并返回前端动态路由所需档案：roles / buttons（去重）/ menus（树）。
 * {@code GET /api/auth/me} 供刷新/重登复用同一套档案装配逻辑。</p>
 *
 * <p>本类位于 {@code common/security}，允许直接用 {@link JdbcTemplate}（ArchUnit 规则 ① 只封堵
 * domains/platform/crosscut）。登录查密码哈希是安全边界的特例；菜单/按钮/角色档案装配委托给
 * {@link AuthProfileService}（MyBatis-Plus，实时读库）。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProfileService authProfileService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuthProfileService authProfileService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProfileService = authProfileService;
    }

    @PublicDataScope(reason = "登录接口，不读写业务数据")
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT \"id\", \"passwordHash\", \"accountStatus\" FROM \"sys_user\" WHERE \"username\" = ?",
                req.username());
        if (rows.isEmpty()) {
            throw BizException.unauthorized();
        }
        Map<String, Object> row = rows.get(0);
        String hash = (String) row.get("passwordHash");
        if (hash == null || !passwordEncoder.matches(req.password(), hash)) {
            throw BizException.unauthorized();
        }
        if (!"ACTIVE".equals(String.valueOf(row.get("accountStatus")))) {
            throw BizException.unauthorized();
        }

        String userId = (String) row.get("id");
        String token = jwtService.generate(userId);
        AuthProfile profile = authProfileService.load(userId);
        return ApiResponse.ok(AuthResponse.loginData(token, profile));
    }

    @PublicDataScope(reason = "当前用户档案查询，不读写业务数据")
    @GetMapping("/me")
    public Map<String, Object> me() {
        Principal principal = CurrentPrincipal.require();
        AuthProfile profile = authProfileService.load(principal.userId());
        return ApiResponse.ok(AuthResponse.meData(profile));
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }
}
