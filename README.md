# NL2SQL 问数系统

个人级企业问数系统：自然语言提问 → 生成只读 SQL → 执行 → 返回结果，附带企业级可信度保障。

**核心定位**：每个答案都可信赖，不准时能告诉你它不准，出了问题能追溯定位。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.4 + Maven |
| 数据层 | MySQL 8.0 + Flyway + HikariCP + JdbcTemplate |
| SQL 解析 | JSqlParser |
| 缓存 | Caffeine（L1 本地缓存，CacheService 接口抽象，预留 Redis） |
| 模型 | 通义千问 qwen-turbo（DashScope OpenAI 兼容端点，REST 直连） |
| 鉴权 | JWT（HS256）+ BCrypt |

## 架构

```
Vue3 前端（M6） → HTTPS+JWT → Spring Boot 单服务
  ├── 意图识别 + 置信度评估（低→反问澄清）
  ├── 指标语义层匹配（命中→模板SQL，未命中→NL2SQL）
  ├── SQL 校验（JSqlParser AST 级白名单）
  ├── 权限注入（按 role 追加 region WHERE）
  ├── LIMIT 注入 + 超时控制
  ├── 语义缓存（Caffeine，key 含 role 防串读）
  ├── 只读连接执行（双数据源：Flyway 读写 + 查询只读）
  ├── 自我修正（执行报错→重试≤3次）
  ├── 数据溯源 + 审计日志
  └── 降级链（模型超时→缓存兜底→友好提示）
```

## 里程碑进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 基础链路（单表查询跑通） | ✅ |
| M2 | 语义层+安全+权限+审计 | ✅ |
| M3 | 置信度+澄清+溯源+自我修正+反馈 | ✅ |
| M4 | 性能+缓存抽象层+降级+会话写穿透 | ✅ |
| M5 | Schema 变更检测+数据质量+指标口径管理 | ⏳ |
| M6 | 前端+LangFuse+成本监控 | ⏳ |
| M7 | CI/CD+150 用例回归+准确率门禁 | ⏳ |

## 本地启动

### 0. 前置：环境变量

| 变量 | 说明 | 示例 |
|---|---|---|
| `DB_PASSWORD` | MySQL root 密码 | `dili123` |
| `DASHSCOPE_API_KEY` | 通义千问 API key | `sk-...` |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） | 任意长字符串 |

> 敏感信息一律环境变量注入，禁止写进配置文件或提交 git。

### 1. 启动 MySQL（Docker）

```bash
docker run -d --name nl2sql-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=dili123 -e MYSQL_DATABASE=nl2sql mysql:8.0
```

### 2. 造样例数据

```bash
# 用本机 Python（miniconda）
python scripts/generate_orders.py --count 5000
# 需先 pip install pymysql
```

### 3. 启动应用

```bash
# 首次会自动执行 Flyway 迁移（建表 + 预置指标/术语/系统账号）
DB_PASSWORD=dili123 DASHSCOPE_API_KEY=sk-xxx JWT_SECRET=xxx \
  mvn spring-boot:run
```

### 4. 验证

```bash
# 登录（系统账号由 DataInitializer 预置）
curl -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 问数（带返回的 token）
curl -X POST http://127.0.0.1:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"question":"总销售额是多少"}'
```

### 预置系统账号

| 账号 | 密码 | 角色 | 数据范围 |
|---|---|---|---|
| admin | admin123 | admin | all |
| operator | operator123 | operator | 华东,华南 |
| product | product123 | product | all |
| manager | manager123 | manager | all |

## 测试

```bash
# 单元测试（默认，不含集成测试）
mvn test

# 集成测试（连真实 MySQL + qwen，会调用 LLM）
mvn test -Dtest=QueryEndToEndIntegrationTest
```

## 项目规范（Spec Coding）

本项目采用 Spec Coding，正式代码严格对齐 Spec。详见：

- `1-NL2SQL项目章程_完整版.md` — PRD/愿景
- `SPEC_CODING_STANDARD_v2.md` — Spec 写作标准
- `docs/spec/*.md` — 交付 Spec（按里程碑）
- `docs/decisions/ADR-*.md` — 人拍板的决策记录
- `CLAUDE.md` — 项目级约定
- `git命令说明.md` — Git 操作手册

**关键约定**：
1. 行为变更顺序：先改 Spec → 更新变更记录 → 再改代码；
2. 代码回链 `// REQ-xxx`，测试回链 `TC-xxx`；
3. 冲突裁决：ADR > Spec > 章程；
4. git 操作由开发者手动执行，AI 只输出命令。
