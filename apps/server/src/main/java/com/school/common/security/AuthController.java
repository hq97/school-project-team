package com.school.common.security;

import com.school.common.scope.PublicDataScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 登录接口（白名单，无需认证）。
 *
 * <p>校验用户名密码，签发 JWT。用户名不存在 / 密码错误 / 账号停用统一返 401（防账号枚举）。</p>
 *
 * <p>本类位于 {@code common/security}，允许直接用 {@link JdbcTemplate}（ArchUnit 规则 ① 只封堵
 * domains/platform/crosscut）。登录查密码哈希是安全边界的特例，不经过 ScopeQueryBuilder。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        String token = jwtService.generate((String) row.get("id"));
        return Map.of("token", token);
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }
}
