# NL2SQL 问数 Agent

**Demo：http://&lt;你的域名&gt;/**  ·  部署后将域名或服务器地址替换此行；页面内有示例问题可点击演示

---

业务分析师不用写 SQL，自然语言提问 → 自动生成只读 SQL → 返回查询结果。附带完整的安全保障（权限行级过滤、只读连接、SQL AST 白名单校验）和可观测性（Langfuse 全链路 trace + LLM-as-Judge 自动评估）。

## 架构

```
┌──────────────┐     ┌──────────────────────────────────┐
│  Vue3 前端    │────▶│        Spring Boot 3.4 单服务       │
│  (静态资源)   │     │  ┌─────────────────────────────┐  │
└──────────────┘     │  │ 意图识别 → 指标匹配 → NL2SQL │  │
                     │  │ → SQL AST 校验 → 权限注入      │  │
┌──────────────┐     │  │ → 只读连接执行 → 数据溯源      │  │
│  Langfuse    │◀────│  │ → 审计日志 + 置信度评估        │  │
│  (Cloud)     │     │  └─────────────────────────────┘  │
└──────────────┘     │     ▲         ▲        ▲          │
                     │     │         │        │          │
                     │  MySQL 8.0  Caffeine  通义千问     │
                     │  (只读连接)  (L1缓存)  qwen-turbo  │
                     └──────────────────────────────────┘
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.4 · Maven |
| 数据层 | MySQL 8.0 · Flyway · HikariCP · JdbcTemplate |
| SQL 安全 | JSqlParser（AST 白名单校验 + 只读连接） |
| 缓存 | Caffeine（L1 本地缓存，CacheService 接口抽象） |
| LLM | 通义千问 qwen-turbo（DashScope OpenAI 兼容端点，自研 RestClient 直连） |
| 鉴权 | JWT（HS256）+ BCrypt · 行级权限（role → region WHERE） |
| 可观测 | Langfuse Cloud · OpenTelemetry SDK + OTLP/HTTP v4 原生管线（trace + generation + token 用量 + LLM-as-Judge 自动评估） |
| 部署 | Docker MySQL · Nginx 反代 · systemd · Ubuntu 22.04 · 阿里云 ECS |

## 工程亮点

### 1. Spec Coding 方法论驱动开发

每个功能对应 Spec 需求编号（REQ-xxx），代码中回链，测试用例回链 TC-xxx。冲突裁决链：ADR（架构决策记录）> Spec > 项目章程。不是 prompt 工程，是有章可循的软件工程。

### 2. Langfuse 全链路可观测 + LLM-as-Judge 自动评估

初期用自研 RestClient 直连 legacy `/api/public/ingestion` 快速跑通；后续因评估器永不触发深挖官方文档，发现 **legacy 通道在 Langfuse v4 中走兼容双写、评估器管线只在原生 OTel 通道上运行**，随即切换到官方推荐的 **OpenTelemetry Java SDK + OTLP/HTTP v4 原生管线**（`opentelemetry-sdk` + `opentelemetry-exporter-otlp`），覆盖：
- Root observation（用户问题 / SQL 结果 + trace 级 metadata：耗时 / 置信度 / 缓存命中 / 角色）
- Generation 子 observation（模型名 + token 用量 input/output/total）
- **LLM-as-Judge 自动评估**：用 DeepSeek API 当裁判，`isRootObservation=true` 规则命中每条查询的 root span，0~1 分评分，新 trace 秒级可见、分数瞬间产生（legacy 通道延迟 9 分钟且评估器永不触发）

### 3. 真实生产 Bug 排查（面试重点）

#### Bug A：Langfuse token 用量不显示——generation 事件变孤儿

**现象**：trace 能看到、Langfuse Home 页有统计，但 Tracing 页没有 generation 子节点、token 用量全为 0。

**根因**：`generation-create` 事件的 `traceId` 放在了事件顶层，而 Langfuse v3 ingestion schema 要求放在 `body` 里。服务端静默拒绝了关联，generation 变成"孤儿 observation"。同时 `LangfuseClient.send()` 用 `toBodilessEntity()` 丢弃了响应体，`207 Multi-Status` 里的 `errors` 数组被完全忽略。

**修复**：
- `LangfuseObservability.java`：`traceId` 从事件顶层移入 `genBody.traceId`
- `LangfuseClient.java`：改为 `toEntity(String.class)`，成功时打 INFO 日志含 `successes/errors` 明细，HTTP 拒绝时打 WARN 含状态码和响应体

**经验**：集成第三方 API 时，**永远不要丢弃响应体**——静默失败比显式报错更难排查。

#### Bug B：前端历史记录时间差 8 小时——MySQL 容器时区 + JDBC 翻译谎言

**现象**：前端显示的历史记录时间比北京时间晚 8 小时，但 Langfuse 里的时间是对的。

**根因**：三件事错配：
1. MySQL 容器 `docker run` 时没加 `-e TZ=Asia/Shanghai`，mysqld 按 UTC 写入墙钟时间
2. JDBC URL 声明 `serverTimezone=Asia/Shanghai`——驱动把服务器给的 UTC 墙钟"当作北京时间"来理解，凭空丢了 8 小时
3. Langfuse 走的是完全不同的链路：`Instant.now()`（ECS 系统是 CST）→ 带 Z 的 ISO-8601 → 浏览器按本地时区渲染，全程无时区歧义

**修复**：重建 MySQL 容器时加 `-e TZ=Asia/Shanghai`。旧记录自动修复——TIMESTAMP 列存的是 epoch 时刻值，不是墙钟时间，读取时按会话时区换算，标签改对了历史数据自然就对了。

**经验**：时区类问题永远分别确认三样——**存的什么类型（TIMESTAMP vs DATETIME）、容器实际时区、驱动声明时区**。Langfuse 走 Instant + ISO-8601 的做法值得借鉴。

#### Bug C：LLM-as-Judge 评估器永不触发——legacy ingestion 与 v4 管线隔离

**现象**：Langfuse Evaluators 页 `sql-accuracy` 规则配置无误（isRootObservation=true、采样率 100%、DeepSeek 裁判模型连通），但 "No recent runs"，单条 trace Scores 面板永远空白。期间 trace 本身能显示（延迟约 9 分钟）、token 用量也对。

**根因**：项目用自研 RestClient 调 `POST /api/public/ingestion`（legacy v3 事件格式），而 Langfuse Cloud 已升级到 v4 架构——legacy 请求走**兼容双写通道**，数据能异步回填到 Tracing 页（延迟 9~15 分钟），但**评估器管线、Scores v3 API、v2 Observations API 全部只在原生 v4 OTLP 通道上跑**，中间没有桥接。官方在迁移文档里明确声明 legacy endpoint 于 2026-11-16 下线，Java SDK 0.3.0 已彻底删除 tracing API，只保留 prompts/scores/查询等管理接口。

**修复**：
- `pom.xml` 引入 `opentelemetry-sdk` + `opentelemetry-exporter-otlp`（OTel 1.49.0，覆盖 Spring Boot BOM 默认的 1.43.0）
- 新增 `LangfuseOtelTracer`：`SdkTracerProvider` + `BatchSpanProcessor(1s)` + `OtlpHttpSpanExporter` 指向 `{base}/api/public/otel/v1/traces`，请求头带 `Authorization: Basic base64(pk:sk)` + `x-langfuse-ingestion-version: 4`；包一层 `LoggingSpanExporter` 代理导出结果（成功 INFO / 失败 WARN），延续 Bug A 的教训——**永远不让第三方上报静默失败**
- 重写 `LangfuseObservability`：batch JSON → 一个 root span（`nl2sql-query`，承载整体 input/output）+ 一个 generation 子 span（`type=generation`、`model.name`、`usage_details` JSON），`langfuse.trace.metadata.*` 复制到两个 span 以便 v4 observation 过滤/聚合
- 对外 `record()` 签名不变 → 调用方零改动 → 部署只换 jar，三个 `LANGFUSE_*` 环境变量原样沿用

**验证证据**：迁移后 Scores 页 `sql-accuracy` 第一条打分在查询后 <30 秒产生；Tracing 页新 trace 秒级出现；Langfuse "Action required" 面板显示🟢 "Detected V4-compatible instrumentation"。

**经验**：集成第三方平台要**紧跟官方 deprecation 时间线**，不要只靠"数据能显示就 OK"判断——下游管线（评估、搜索、API）可能早已弃用兼容通道。官方 README 里一句 "OpenTelemetry is the only supported way going forward" 要读透。

#### Bug D：2G ECS 深夜 OOM——swap thrashing 导致整机无响应

**现象**：服务器每天凌晨卡死，SSH 连不上，重启后恢复。阿里云监控报"实例存储性能达到规格上限"（磁盘带宽饱和）。

**根因**：2G 内存不够 JVM 960m + MySQL 512m + 系统进程。内存不足时疯狂换页（swap thrashing），系统盘的读写带宽被 swap 占满，正常 I/O 请求被饿死。earlyoom 服务尚未安装，OOM 前无预警。

**修复**：
- JVM：`-Xms256m -Xmx704m -XX:MaxMetaspaceSize=256m`
- MySQL：`docker update --memory=384m mysql`（迁移时临时扩 768m，迁完调回）
- 系统：`vm.swappiness=10`（优先回收 page cache 而非匿名页换出）
- earlyoom：安装并启用，内存 <10% 时 kill 最高内存进程而非让整机冻结

**经验**：2G ECS 跑 Java 应用的安全线：JVM 堆 < 700m + MySQL < 400m + 预留系统 + earlyoom 兜底。监控上看 swap 使用量比看 free -h 更早预警。

### 4. 生产级部署实践

2 核 2G ECS 跑通全链路，包含：
- systemd 服务守护 + JVM 调优参数固化
- Nginx 反代 /api/ → 8080（8080 不对外暴露）
- Docker MySQL 挂载宿主机卷（重建容器不丢数据）
- 敏感信息一律环境变量注入，零硬编码
- HTTPS 支持（有域名 Let's Encrypt / 无域名自签名证书）

## 项目规范

Spec Coding 方法论，代码回链 `// REQ-xxx`，测试回链 `TC-xxx`。

| 文档 | 说明 |
|---|---|
| `docs/部署教程_保姆级.md` | 手把手部署教程（含三种生产 bug 排查） |
| `docs/` | Spec 文档 + ADR 架构决策记录 + 项目章程 |

## 本地启动

需要：JDK 17、Maven、Docker。完整步骤见 [部署教程](docs/部署教程_保姆级.md)。

```bash
# 1. 启动 MySQL
docker run -d --name nl2sql-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=xxx -e MYSQL_DATABASE=nl2sql \
  -e TZ=Asia/Shanghai mysql:8.0

# 2. 造样例数据（可选）
python scripts/generate_orders.py --count 5000

# 3. 启动应用
DB_PASSWORD=xxx DASHSCOPE_API_KEY=sk-xxx JWT_SECRET=xxx mvn spring-boot:run

# 4. 前端（另开终端）
cd frontend && npm run dev
```

系统预置账号：`admin / admin123`（admin）、`operator / operator123`（华东+华南）、`product / product123`、`manager / manager123`。
