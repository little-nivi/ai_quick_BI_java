-- REQ-309 schema_fingerprint 表（Schema 指纹，P1-1）
CREATE TABLE IF NOT EXISTS schema_fingerprint (
    table_name   VARCHAR(100) PRIMARY KEY,
    fingerprint  VARCHAR(64)  NOT NULL,
    detected_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-310 schema_changes 表（变更记录，P1-2，章程）
CREATE TABLE IF NOT EXISTS schema_changes (
    change_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_type  VARCHAR(20)  NOT NULL,
    table_name   VARCHAR(100) NOT NULL,
    old_value    TEXT         NULL,
    new_value    TEXT         NULL,
    detected_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified     BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- REQ-311 data_quality_alerts 表（数据质量告警，P1-3，章程）
CREATE TABLE IF NOT EXISTS data_quality_alerts (
    alert_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type    VARCHAR(50)  NOT NULL,
    table_name    VARCHAR(100) NOT NULL,
    field_name    VARCHAR(100) NULL,
    current_value VARCHAR(200) NULL,
    threshold     VARCHAR(100) NULL,
    severity      VARCHAR(10)  NOT NULL DEFAULT 'warning',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged  BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
