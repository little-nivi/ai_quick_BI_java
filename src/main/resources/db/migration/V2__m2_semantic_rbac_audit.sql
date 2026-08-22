-- REQ-302 users 表（含系统账号，ADR D-07）
CREATE TABLE IF NOT EXISTS users (
    user_id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username           VARCHAR(50)  NOT NULL,
    age                INT          NULL,
    gender             VARCHAR(10)  NULL,
    city               VARCHAR(50)  NULL,
    register_date      DATETIME     NULL,
    vip_level          VARCHAR(20)  NULL,
    role               VARCHAR(20)  NOT NULL DEFAULT 'operator',
    data_scope         VARCHAR(200) NOT NULL DEFAULT 'all',
    password_hash      VARCHAR(100) NULL,
    is_system_account  BOOLEAN      NOT NULL DEFAULT FALSE,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-303 metric_definitions 表（指标语义层）
CREATE TABLE IF NOT EXISTS metric_definitions (
    metric_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_name     VARCHAR(100) NOT NULL,
    synonyms        VARCHAR(500) NULL,
    expression      TEXT         NOT NULL,
    related_tables  VARCHAR(200) NOT NULL,
    definition      TEXT         NOT NULL,
    template_sql    TEXT         NOT NULL,
    version         INT          NOT NULL DEFAULT 1,
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft',
    created_by      BIGINT       NULL,
    approved_by     BIGINT       NULL,
    approved_at     TIMESTAMP    NULL,
    changelog       TEXT         NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-304 metric_lineage 表（指标血缘）
CREATE TABLE IF NOT EXISTS metric_lineage (
    lineage_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id         BIGINT        NOT NULL,
    downstream_report VARCHAR(200)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-305 business_glossary 表（业务术语映射）
CREATE TABLE IF NOT EXISTS business_glossary (
    glossary_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term        VARCHAR(100) NOT NULL,
    mapping     VARCHAR(200) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-306 audit_logs 表（审计日志）
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NULL,
    role          VARCHAR(20)  NULL,
    question      TEXT         NOT NULL,
    generated_sql TEXT         NULL,
    result_hash   VARCHAR(64)  NULL,
    confidence    VARCHAR(10)  NULL,
    latency_ms    INT          NULL,
    token_used    INT          NULL,
    cache_hit     BOOLEAN      NOT NULL DEFAULT FALSE,
    ip_address    VARCHAR(45)  NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
