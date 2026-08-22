package com.nl2sql.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与校验（REQ-402、ADR D-11：HS256，有效期 24h）。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${nl2sql.jwt.secret:}") String secret,
                   @Value("${nl2sql.jwt.expiration-hours:24}") long expirationHours) {
        // 若未配置密钥，用固定占位（仅 dev；生产必须经环境变量注入）
        String actual = (secret == null || secret.isBlank()) ? "dev-only-insecure-secret-change-me-32bytes-min" : secret;
        this.key = Keys.hmacShaKeyFor(actual.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationHours * 3600_000L;
    }

    public String generate(Long userId, String role, String dataScope) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("data_scope", dataScope)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 校验并解析 token；无效/过期抛 {@link io.jsonwebtoken.JwtException}。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
