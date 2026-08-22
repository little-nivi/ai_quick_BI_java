-- M2 种子数据：20 条指标定义 + 30 条业务术语映射（章程 §测试方案）
-- 指标全部 status='published'，M2 即可参与匹配。

INSERT INTO metric_definitions
(metric_name, synonyms, expression, related_tables, definition, template_sql, version, status, created_by, approved_by, approved_at, changelog)
VALUES
('销售额', '营收,收入,GMV', 'SUM(amount)', 'orders', '含税金额，不含退款', 'SELECT SUM(amount) AS 销售额 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('订单量', '订单数,下单量', 'COUNT(*)', 'orders', '订单总条数', 'SELECT COUNT(*) AS 订单量 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('客单价', '单均金额,平均客单价', 'SUM(amount)/COUNT(DISTINCT order_id)', 'orders', '平均每单金额', 'SELECT SUM(amount)/COUNT(*) AS 客单价 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('销量', '销售量,卖出数量', 'SUM(quantity)', 'orders', '商品销售总件数', 'SELECT SUM(quantity) AS 销量 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('退款金额', '退款,退货金额', 'SUM(amount)', 'orders', '状态为 refunded 的金额合计', 'SELECT SUM(amount) AS 退款金额 FROM orders WHERE status = ''refunded''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('下单用户数', '购买人数,UV', 'COUNT(DISTINCT user_id)', 'orders', '去重下单用户数', 'SELECT COUNT(DISTINCT user_id) AS 下单用户数 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('复购率', '回头率,复购比例', 'COUNT(DISTINCT user_id)', 'orders', '下单≥2次的用户占比（占位口径，M2 简化）', 'SELECT COUNT(DISTINCT user_id) AS 复购率 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('华东销售额', '华东营收', 'SUM(amount)', 'orders', '华东区域销售额', 'SELECT SUM(amount) AS 华东销售额 FROM orders WHERE region = ''华东''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('华北销售额', '华北营收', 'SUM(amount)', 'orders', '华北区域销售额', 'SELECT SUM(amount) AS 华北销售额 FROM orders WHERE region = ''华北''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('华南销售额', '华南营收', 'SUM(amount)', 'orders', '华南区域销售额', 'SELECT SUM(amount) AS 华南销售额 FROM orders WHERE region = ''华南''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('西南销售额', '西南营收', 'SUM(amount)', 'orders', '西南区域销售额', 'SELECT SUM(amount) AS 西南销售额 FROM orders WHERE region = ''西南''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('东北销售额', '东北营收', 'SUM(amount)', 'orders', '东北区域销售额', 'SELECT SUM(amount) AS 东北销售额 FROM orders WHERE region = ''东北''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('西北销售额', '西北营收', 'SUM(amount)', 'orders', '西北区域销售额', 'SELECT SUM(amount) AS 西北销售额 FROM orders WHERE region = ''西北''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('app渠道订单量', 'app订单,app下单', 'COUNT(*)', 'orders', 'app 渠道订单数', 'SELECT COUNT(*) AS app渠道订单量 FROM orders WHERE channel = ''app''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('web渠道订单量', 'web订单,网页订单', 'COUNT(*)', 'orders', 'web 渠道订单数', 'SELECT COUNT(*) AS web渠道订单量 FROM orders WHERE channel = ''web''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('小程序订单量', '小程序订单,miniapp订单', 'COUNT(*)', 'orders', 'miniapp 渠道订单数', 'SELECT COUNT(*) AS 小程序订单量 FROM orders WHERE channel = ''miniapp''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('线下订单量', '线下订单,offline订单', 'COUNT(*)', 'orders', 'offline 渠道订单数', 'SELECT COUNT(*) AS 线下订单量 FROM orders WHERE channel = ''offline''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('已完成订单量', '完成订单,completed订单', 'COUNT(*)', 'orders', 'status=completed 的订单数', 'SELECT COUNT(*) AS 已完成订单量 FROM orders WHERE status = ''completed''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('退款订单量', '退款单量,cancelled订单', 'COUNT(*)', 'orders', 'status=refunded 的订单数', 'SELECT COUNT(*) AS 退款订单量 FROM orders WHERE status = ''refunded''', 1, 'published', 1, 1, NOW(), 'M2 预置'),
('平均订单金额', '平均金额,均额', 'AVG(amount)', 'orders', '订单金额平均值', 'SELECT AVG(amount) AS 平均订单金额 FROM orders', 1, 'published', 1, 1, NOW(), 'M2 预置');

INSERT INTO business_glossary (term, mapping) VALUES
('卖得最好', '销量最高'), ('卖得好', '销量高'), ('大客户', '消费金额大于10000的用户'),
('最贵', '价格最高'), ('最便宜', '价格最低'), ('最近', '最新'), ('上个月', '近30天'),
('本月', '本月至今'), ('今年', '本年度'), ('去年', '上一年度'), ('Q1', '第一季度'),
('Q2', '第二季度'), ('Q3', '第三季度'), ('Q4', '第四季度'), ('同比', '与去年同期相比'),
('环比', '与上一周期相比'), ('爆款', '销量最高的商品'), ('滞销', '销量最低的商品'),
('高价值', '客单价高'), ('低价值', '客单价低'), ('新用户', '注册时间近的用户'),
('老用户', '注册时间早的用户'), ('活跃', '有下单行为'), ('沉默', '无下单行为'),
('复购', '多次购买'), ('退货', '已退款'), ('下单', '创建订单'), ('成交', '已完成订单'),
('流失', '长时间未下单'), ('增长', '数量上升');
