-- REQ-308 session_records 表（会话写穿透，ADR D-24）
CREATE TABLE IF NOT EXISTS session_records (
    record_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    question      TEXT        NOT NULL,
    generated_sql TEXT        NULL,
    cache_hit     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
