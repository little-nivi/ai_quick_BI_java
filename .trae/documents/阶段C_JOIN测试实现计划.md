# 阶段 C：JOIN 20 条测试用例实现计划

## Context（为什么做这个）

当前 130 条用例全是**单表查询**（仅 `orders` 表），LLM 容易蒙对 SQL 形态，无法暴露 Schema 幻觉风险。阶段 C 通过新建 `customers`/`products` 关联表 + 20 条 JOIN 用例，把 LLM 推到"必须真正理解表关系"的场景，让 SQL 生成链路接受 JOIN 复杂度考验，同时 PermissionInjector 的权限注入在多表 SQL 下仍能工作。

成功标准：20 条 JOIN 用例全量跑通，准确率 ≥ 80%，operator 用户跑 JOIN 问题时权限注入不报字段歧义错误。

## 关键设计决策

### 决策 1：customers 表**不带** region 字段

**理由**：[PermissionInjector.java#L34](file:///d:/mywork/20260818_java_agent/src/main/java/com/nl2sql/query/PermissionInjector.java#L34) 注入的是裸 `AND region IN (...)`，不带表别名前缀。若 customers 也有 region 字段，JOIN SQL 会因字段歧义报错。让 region 字段**仅存在于 orders 表**，可保持单表场景与 JOIN 场景的权限注入逻辑零修改。

customers 表只承载"客户级属性"（类型、等级、注册时间），不与 orders 的地理维度重叠。

### 决策 2：用 Flyway V7 建表 + 灌少量样例数据，大量数据仍用 Python 脚本

V7 只建表 + 灌 20 条样例 customers/20 条样例 products（保证基本 JOIN 能跑通）。若需大数据量 JOIN 性能验证，另写 `scripts/generate_customers_products.py` 灌 5000 条 customers + 20 条 products（products 不需多，本来 product_id 就在 1..20 范围）。

### 决策 3：JOIN 用例判定逻辑放宽

单表用例判定靠关键词（`["SUM"]` 等），JOIN SQL 形态多变不适合关键词判定。JOIN 用例判定规则：
- `code == 2000` 且 `sql` 包含 `JOIN`（不区分大小写）→ 通过
- 危险操作用例（如"删除某客户的订单"）→ `code != 2000` 即通过

这恰好覆盖 Schema 幻觉场景：若 LLM 编造 customers 不存在的字段，SQL 执行返回 5000 → 用例失败 → 暴露幻觉。

## 4 层改动清单

### 层 1：数据库迁移（V7）

新建 `src/main/resources/db/migration/V7__m6_join_tables.sql`：

```sql
-- M6 阶段C：JOIN 关联表
-- customers 不带 region 字段，避免与 orders.region 冲突导致权限注入歧义
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
    category VARCHAR(50) NOT NULL,   -- 数码/服饰/食品/家居
    price    DECIMAL(10,2) NOT NULL,
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 灌样例数据（20 customers + 20 products，product_id 与 generate_orders.py 中 CATEGORY_COUNT=20 对齐）
INSERT INTO customers (id, name, type, level, register_date) VALUES
(1, '客户A', '企业', '金卡', '2024-01-15 10:00:00'),
... 共 20 条，覆盖 type×level 组合
INSERT INTO products (id, name, category, price) VALUES
(1, '商品1', '数码', 199.00),
... 共 20 条，覆盖 4 个 category
```

### 层 2：SqlGenerator schema 提示词

[SqlGenerator.java#L24-L37](file:///d:/mywork/20260818_java_agent/src/main/java/com/nl2sql/query/SqlGenerator.java#L24-L37) 的 `ORDERS_SCHEMA` 常量扩展为 `DB_SCHEMA`，追加 customers 和 products 的 CREATE TABLE 语句 + 关联关系说明：

```java
private static final String DB_SCHEMA = """
        CREATE TABLE orders (
          order_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
          user_id    BIGINT UNSIGNED NOT NULL,   -- 关联 customers.id
          product_id BIGINT UNSIGNED NOT NULL,   -- 关联 products.id
          amount     DECIMAL(10,2)  NOT NULL,
          quantity   INT UNSIGNED   NOT NULL,
          price      DECIMAL(10,2)  NOT NULL,
          order_date DATETIME       NOT NULL,
          region     VARCHAR(50)    NOT NULL,
          status     VARCHAR(20)    NOT NULL DEFAULT 'completed',
          channel    VARCHAR(50)    NOT NULL
        );
        CREATE TABLE customers (
          id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
          name          VARCHAR(100)   NOT NULL,
          type          VARCHAR(20)    NOT NULL,   -- 个人/企业
          level         VARCHAR(20)    NOT NULL,   -- 普通/银卡/金卡/钻石
          register_date DATETIME       NOT NULL
        );
        CREATE TABLE products (
          id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
          name     VARCHAR(100) NOT NULL,
          category VARCHAR(50) NOT NULL,   -- 数码/服饰/食品/家居
          price    DECIMAL(10,2) NOT NULL
        );
        关联关系：
        - orders.user_id = customers.id
        - orders.product_id = products.id
        - region 字段仅在 orders 表上
        """;
```

同步更新 [SYSTEM_PROMPT](file:///d:/mywork/20260818_java_agent/src/main/java/com/nl2sql/query/SqlGenerator.java#L39-L48) 末尾追加 1 条 JOIN few-shot 示例，提示 LLM 何时需要 JOIN：
```
示例4：问题"企业客户的订单数" -> {"sql":"SELECT COUNT(*) FROM orders o JOIN customers c ON o.user_id=c.id WHERE c.type='企业'","explanation":"按客户类型过滤订单","is_query":true,"confidence":0.85}
```

### 层 3：权限注入适配（验证不动代码）

[PermissionInjector.inject](file:///d:/mywork/20260818_java_agent/src/main/java/com/nl2sql/query/PermissionInjector.java#L17) 当前逻辑在 JOIN 场景下**无需修改**——因为：
- region 字段仅在 orders 表上（决策 1）
- LLM 生成的 JOIN SQL 若用别名引用字段，权限注入追加的裸 `region IN` 会被 MySQL 在单义字段下解析成功
- 若 LLM 用 `o.region` 别名引用，裸 `region` 会被 MySQL 自动解析到唯一拥有该字段的 orders 表

**唯一边界**：若 LLM 生成的 SQL 在 WHERE 子句里给 region 加了别名（`o.region IN` 形式），PermissionInjector 注入的 `AND region IN` 仍然落到 orders 表——这是可接受的行为。

**无需改 PermissionInjector**，但需在 V7 之后跑 [eval_accuracy.py](file:///d:/mywork/20260818_java_agent/scripts/eval_accuracy.py) 的权限组（PERM_CASES）验证 JOIN 场景下权限注入仍能工作。

### 层 4：评测脚本加 20 条 JOIN 用例

[eval_accuracy.py](file:///d:/mywork/20260818_java_agent/scripts/eval_accuracy.py) 改动：

1. 新增常量 `CAT_JOIN = "JOIN多表查询"`
2. 新增 `JOIN_CASES` 列表，20 条用例（id 201-220）：
   - 6 条 customers JOIN（企业客户订单数、金卡客户客单价、老客户销量等）
   - 6 条 products JOIN（数码类销量、服饰类销售额、最贵商品品类等）
   - 4 条三表 JOIN（企业客户的数码类订单、金卡客户买的最贵商品等）
   - 4 条边界用例（删除某客户订单→NOT_2000、问某不存在字段的 JOIN→幻觉场景）
3. `category_of(cid)` 函数扩展：`if 201 <= cid <= 220: return CAT_JOIN`
4. `run_normal` 流程把 JOIN_CASES 合并到 NORMAL_CASES 一起跑
5. 判定函数适配：JOIN 用例的判定逻辑用 `("JOIN",)` 而非 SQL 关键词，约定 `passed = (code == 2000 and "JOIN" in sql.upper())`
6. QUICK 模式可加 2 条 JOIN 用例（如 #201、#207）

## 验证步骤（端到端）

1. **本地编译**：`mvn clean package -DskipTests` 通过
2. **服务器部署**：用户手动 SCP jar + 重启服务（按既定流程）
3. **V7 迁移自动执行**：服务启动时 Flyway 自动跑 V7，可用 `mysql -e "SHOW TABLES"` 验证 customers/products 表已建
4. **样例数据验证**：`mysql -e "SELECT COUNT(*) FROM customers; SELECT COUNT(*) FROM products;"` 都 ≥ 20
5. **手动问一条 JOIN**：`curl ... -d '{"question":"企业客户的订单数"}'` 期望 SQL 含 `JOIN customers`
6. **跑全量评测**：`python scripts/eval_accuracy.py --base http://127.0.0.1:8080`（在服务器或本地均可），验证 JOIN 组准确率 ≥ 80%
7. **权限隔离验证**：用 operator 账号问"企业客户的订单数"，检查 SQL 中既含 JOIN 又含 `region IN ('华东','华北')`

## 用户决策点（在 NotifyUser 时确认）

- **决策 1**：customers 不带 region 字段（避免权限注入歧义）→ 推荐方案
- **决策 2**：V7 灌 20 条样例 customers/products 数据，不做大数据量 JOIN 性能测试（如有需要再补脚本）
- **决策 3**：JOIN 用例判定逻辑用 `("JOIN",)` 而非关键词校验
