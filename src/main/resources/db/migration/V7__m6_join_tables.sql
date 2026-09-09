-- M6 阶段C：JOIN 关联表（customers + products）
-- 设计决策：customers 表不带 region 字段，避免与 orders.region 在 JOIN 场景下
--           导致 PermissionInjector 注入的裸 "region IN (...)" 因字段歧义报错。
--           region 维度仅在 orders 表上承载。

CREATE TABLE IF NOT EXISTS customers (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)   NOT NULL,
    type          VARCHAR(20)    NOT NULL,   -- 个人/企业
    level         VARCHAR(20)    NOT NULL,   -- 普通/银卡/金卡/钻石
    register_date DATETIME       NOT NULL,
    INDEX idx_type (type),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
    id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    category VARCHAR(50)  NOT NULL,   -- 数码/服饰/食品/家居
    price    DECIMAL(10,2) NOT NULL,
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- products 样例数据：20 条，与 generate_orders.py 的 CATEGORY_COUNT=20 对齐
-- product_id 1..20 在 orders 表中已存在，可直接 JOIN
INSERT INTO products (id, name, category, price) VALUES
(1,  '无线蓝牙耳机',  '数码', 199.00),
(2,  '智能手表',      '数码', 899.00),
(3,  '机械键盘',      '数码', 359.00),
(4,  '4K显示器',      '数码', 1599.00),
(5,  '移动硬盘',      '数码', 459.00),
(6,  '纯棉T恤',       '服饰', 89.00),
(7,  '牛仔裤',        '服饰', 259.00),
(8,  '运动鞋',        '服饰', 499.00),
(9,  '羽绒服',        '服饰', 899.00),
(10, '商务衬衫',      '服饰', 329.00),
(11, '有机牛奶',      '食品', 69.00),
(12, '进口车厘子',    '食品', 159.00),
(13, '黑巧克力',      '食品', 49.00),
(14, '精品咖啡豆',    '食品', 129.00),
(15, '速冻水饺',      '食品', 39.00),
(16, '北欧风落地灯',  '家居', 399.00),
(17, '人体工学椅',    '家居', 1299.00),
(18, '加湿器',        '家居', 199.00),
(19, '记忆棉枕',      '家居', 159.00),
(20, '实木书架',      '家居', 699.00);

-- customers 样例数据：仅 5 条占位（让 V7 自检能跑通）。
-- 真正的 2000 条业务数据由 scripts/generate_customers.py 灌入，
-- 覆盖 orders.user_id 范围 1..2000，保证 JOIN 不返回空集。
INSERT INTO customers (id, name, type, level, register_date) VALUES
(1,    '测试客户1', '企业', '金卡',   '2024-01-15 10:00:00'),
(2,    '测试客户2', '个人', '普通',   '2024-02-20 14:30:00'),
(3,    '测试客户3', '企业', '钻石',   '2024-03-10 09:15:00'),
(100,  '测试客户100', '个人', '银卡', '2024-05-01 11:00:00'),
(1000, '测试客户1000','企业', '金卡', '2024-08-15 16:45:00');
