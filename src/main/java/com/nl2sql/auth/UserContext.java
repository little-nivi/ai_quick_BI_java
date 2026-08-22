package com.nl2sql.auth;

/**
 * 当前请求用户上下文（ThreadLocal），由 AuthFilter 写入、请求结束清理。
 * 贯穿权限注入（REQ-507）、审计（REQ-508）。
 */
public final class UserContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户（从 JWT claims 解析）。 */
    public record AuthUser(Long userId, String role, String dataScope) {
    }
}
