-- REQ-301 orders 建表（M1 唯一业务表）
-- ADR-0002 D1/D2/D3：字段类型、status 默认值、索引

CREATE TABLE IF NOT EXISTS orders (
    order_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    amount     DECIMAL(10,2)  NOT NULL,
    quantity   INT UNSIGNED   NOT NULL,
    price      DECIMAL(10,2)  NOT NULL,
    order_date DATETIME       NOT NULL,
    region     VARCHAR(50)    NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'completed',
    channel    VARCHAR(50)    NOT NULL,
    INDEX idx_order_date (order_date),
    INDEX idx_user_id (user_id),
    INDEX idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
