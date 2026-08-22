-- REQ-307 feedback_queue 表（用户反馈闭环，P1-4）
CREATE TABLE IF NOT EXISTS feedback_queue (
    feedback_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    question           TEXT         NOT NULL,
    generated_sql      TEXT         NULL,
    result_hash        VARCHAR(64)  NULL,
    feedback_type      VARCHAR(10)  NOT NULL,
    user_comment       TEXT         NULL,
    user_corrected_sql TEXT         NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'pending',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at       TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
