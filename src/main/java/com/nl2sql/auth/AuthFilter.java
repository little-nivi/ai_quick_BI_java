package com.nl2sql.auth;

import com.nl2sql.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 鉴权过滤器：解析 Authorization Bearer token，写入 UserContext（REQ-403、SR-5）。
 * 放行 /api/v1/auth/login；其余 /api/v1/** 需鉴权。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public AuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 登录接口放行
        if (path.equals("/api/v1/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 仅鉴权 /api/v1/** 业务接口
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            String dataScope = claims.get("data_scope", String.class);
            UserContext.set(new UserContext.AuthUser(userId, role, dataScope));
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":" + ErrorCode.UNAUTHORIZED.getCode() + ",\"message\":\"未授权\",\"data\":null,\"traceId\":null}");
    }
}
