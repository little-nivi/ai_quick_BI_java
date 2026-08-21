package com.nl2sql.common;

import java.util.UUID;

/**
 * 每请求 traceId 的 ThreadLocal 持有器（贯穿前端→后端→日志，00_总览 §4）。
 */
public final class TraceIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    /** 无请求上下文（如定时任务/异常兜底）时生成新的 traceId。 */
    public static String getOrCreate() {
        String traceId = HOLDER.get();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            HOLDER.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
