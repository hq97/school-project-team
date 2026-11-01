package com.school.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 生成/解析（TS 语义来源：global-auth.guard.ts）。
 *
 * <p>约定：HS256，密钥读 {@code school.jwt.secret}，显式声明 {@code Jwts.SIG.HS256}
 * （对齐 TS 显式 {@code algorithms:['HS256']}，不接受 none/RS 错配攻击面）。</p>
 *
 * <p><b>密钥长度说明（Java 特有适配）</b>：jjwt 的 {@code Keys.hmacShaKeyFor} 要求密钥 ≥ 256 位（32 字节），
 * 而 TS 的 {@code jsonwebtoken} 用原始字符串即可。为保证任意长度 secret 均可启动，
 * 这里对 secret 做一次 SHA-256 派生，得到固定 32 字节的 HMAC-SHA256 密钥（仍是 HS256，只是密钥先规范化）。</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlSeconds;

    public JwtService(@Value("${school.jwt.secret}") String secret,
                      @Value("${school.jwt.access-token-ttl:900}") long accessTokenTtlSeconds) {
        this.key = deriveHmacSha256Key(secret);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String generate(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析并返回 subject（userId）。解析失败抛 {@link io.jsonwebtoken.JwtException}，由过滤器统一兜底。 */
    public String parseUserId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private static SecretKey deriveHmacSha256Key(String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用，无法派生 JWT 密钥", e);
        }
    }
}
