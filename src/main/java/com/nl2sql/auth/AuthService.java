package com.nl2sql.auth;

import com.nl2sql.cache.PermissionCache;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 登录鉴权（REQ-402、ADR D-07：仅系统账号可登录）。
 */
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;
    private final PermissionCache permissionCache;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil, PermissionCache permissionCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
        this.permissionCache = permissionCache;
    }

    public LoginResponse login(String username, String password) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id, username, role, data_scope, password_hash, is_system_account " +
                        "FROM users WHERE username = ?", username);

        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.AUTH_FAILED);
        }
        Map<String, Object> row = rows.get(0);

        boolean isSystem = toBoolean(row.get("is_system_account"));
        String hash = row.get("password_hash") == null ? null : row.get("password_hash").toString();
        if (!isSystem || hash == null || !encoder.matches(password, hash)) {
            throw new BizException(ErrorCode.AUTH_FAILED);
        }

        Long userId = ((Number) row.get("user_id")).longValue();
        String role = (String) row.get("role");
        String dataScope = (String) row.get("data_scope");
        String token = jwtUtil.generate(userId, role, dataScope);
        permissionCache.put(role, dataScope); // REQ-517 权限缓存写入
        return new LoginResponse(token, role, dataScope);
    }

    private boolean toBoolean(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return ((Number) v).intValue() != 0;
    }

    public record LoginResponse(String token, String role, String dataScope) {
    }
}
