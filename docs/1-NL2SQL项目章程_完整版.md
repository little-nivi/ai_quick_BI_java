# 个人级企业NL2SQL问数系统 · 最终版项目章程

## 项目概述

- **项目名称**：个人级企业问数系统（NL2SQL）
- **项目目标**：实现自然语言提问 → 自动生成SQL → 执行查询 → 可视化返回结果的完整链路，并具备企业级可信度保障
- **目标用户**：10-20人（运营、产品、管理层等多角色）
- **数据规模**：脚本生成100-200万条测试数据
- **核心指标**：NL2SQL端到端准确率 ≥ 80%（限定场景下）
- **设计原则**：每个答案都可信赖，不准时能告诉你它不准，出了问题能追溯定位
- **企业级体现**：RBAC权限体系、指标语义层（口径一致）、指标口径管理（版本+审批+血缘）、置信度闭环（避免瞎猜）、反问澄清、数据溯源、审计日志、安全防护、自我修正、性能优化、降级容灾、用户反馈闭环、持续回归测试、数据质量监控、成本与延迟监控、缓存抽象层设计（接口可替换、可观测、可平滑迁移到Redis）、Schema指纹双层存储（Caffeine L1 + MySQL持久化）

---

## 团队分工

大厂真实项目中，NL2SQL系统涉及多角色协作。明确分工是面试中体现"企业级工程思维"的关键。

| 角色 | 职责 | 在本项目中的体现 |
|---|---|---|
| **数据工程师** | 建表、ETL、数据质量监控、Schema变更检测 | 负责MySQL表结构设计与索引优化、定时任务检测Schema变更、数据质量告警 |
| **AI工程师** | Prompt工程、模型调优、评测体系、Few-Shot维护 | 负责NL2SQL链路调优、准确率评测、反馈闭环中负样本标注与示例补充 |
| **后端工程师** | 服务开发、API设计、权限、缓存、性能 | 负责Spring Boot单服务全部业务逻辑、RBAC、Caffeine缓存抽象层、降级策略 |
| **前端工程师** | 可视化、交互、溯源展示、反馈入口 | 负责Vue3界面、ECharts图表、反馈按钮、溯源面板 |
| **产品经理** | 需求定义、指标口径管理、评审审批 | 负责指标口径评审、变更审批、灰度发布决策 |
| **数据分析师** | 指标定义、口径确认、测试验收 | 负责metric_definitions表内容维护、测试用例编写与验收 |

**指标口径争议拍板机制**：数据分析师提出口径定义 → 产品经理评审 → 上线后若需变更，需走变更审批流程并通知下游使用者。

---

## 环境策略

生产环境不是"一套配置跑所有"，不同环境使用不同的模型、数据源和缓存配置，既保证质量又控制成本。

| 环境 | 基座模型 | 数据 | 缓存 | 成本策略 |
|---|---|---|---|---|
| **开发环境（dev）** | qwen-turbo（最低成本） | 测试数据库、10万条样本数据 | Caffeine本地缓存 | 极致省钱，允许准确率偏低 |
| **测试环境（staging）** | qwen-plus（平衡成本与质量） | 150万条测试数据 | Caffeine本地缓存 | 平衡成本与质量，跑完整回归测试 |
| **生产环境（prod）** | qwen-max（保质量） | 150万条模拟生产数据 | Caffeine本地缓存 + 会话持久化 | 质量优先，LangFuse全量观测 |

**配置管理**：使用Spring Boot的 `application-{env}.yml` 多环境配置 + 环境变量注入敏感信息（API Key、数据库密码）。

**敏感配置管理**：所有API Key、数据库密码通过环境变量注入，禁止硬编码在代码或配置文件中。生产环境使用HashiCorp Vault或阿里云KMS管理密钥。CI/CD中加入 `trufflehog` 扫描，防止密钥意外提交到代码仓库。

---

## 系统架构

```
────────────────────────────────────────────────────
│              前端（Vue3 + ECharts）                 │
│   自然语言输入 / 图表渲染 / 反问澄清 / 溯源展示      │
│   角色标识 / 权限提示 / 历史记录 / 用户反馈按钮       │
───────────────────────────────────────────────────
                         │ HTTPS + JWT（含角色信息）
────────────────────────▼─────────────────────────
│           Java 全栈服务（Spring Boot）              │
│                                                    │
│   意图识别 + 置信度评估（低于阈值→反问澄清）        │
│   指标语义层匹配（命中→模板SQL，未命中→NL2SQL）     │
│   Schema召回（BM25粗排→精排，压缩搜索空间）         │
│   SQL生成（按环境配置模型：prod→max, staging→plus, dev→turbo）│
│   SQL校验（安全规则 + 语法校验 + 语义自检）         │
│   权限注入（根据角色自动追加WHERE条件）             │
│   性能优化（LIMIT注入 + 超时控制）                  │
│   SQL执行（MySQL只读连接 + 索引优化）               │
│   结果格式化 + 数据溯源 + 审计日志                  │
│   自我修正（执行报错→回传模型→自动修正，最多3次）    │
│   CacheService（Caffeine L1本地缓存 + MySQL持久化）  │
│     ├── 语义缓存 / 查询缓存 / 权限缓存               │
│     ├── Schema指纹缓存（Caffeine L1 + MySQL持久化）   │
│     ├── 会话缓存（Caffeine L1 + MySQL写穿透）         │
│     ─ 降级：缓存不可用→不缓存，走完整链路            │
│   降级处理（模型超时→qwen-plus→缓存兜底→友好提示）   │
│                                                    │
─────────────────────────────────────────────────
       │              │              │
──────▼────── ───▼────── ──▼────────
│   MySQL     │ │  LangFuse    │ │  定时任务     │
│  业务数据    │ │  全链路观测   │ │  Schema变更  │
│  指标定义    │ │  Token/成本   │ │  数据质量   │
│  指标审批    │ │  质量追踪     │ │  持续回归   │
│  审计日志    │ │              │ │             │
│  用户角色表  │ │              │ │             │
│  反馈队列    │ │              │ │             │
│  数据质量告警│ │              │ │             │
│  Schema指纹  │ │              │ │             │
│  会话记录    │ │              │ │             │
───────────── ────────────── ──────────
```

**架构说明**：

- **Caffeine** 作为 L1 本地缓存内嵌于 Spring Boot 应用进程，承担语义缓存、查询缓存、权限缓存的L1加速，纳秒级读取延迟，零运维成本
- **Schema指纹** 采用 Caffeine L1 缓存（maximumSize=1, expireAfterWrite=1h）+ MySQL 持久化存储的双层设计，MySQL 为单一事实来源
- **MySQL** 承担会话持久化（写穿透策略）、Schema指纹存储、反馈队列，保证数据不丢失
- **LangFuse** 负责全链路可观测性（Traces、Token消耗、成本、质量追踪）
- **定时任务** 负责 Schema 变更检测、数据质量监控、持续回归测试

---

## 完整技术栈清单

| 层级 | 组件 | 技术选型 | 说明 |
|---|---|---|---|
| **前端** | 框架 | Vue3 + TypeScript | 响应式UI |
| | 图表 | ECharts | 支持SQL结果自动渲染 |
| | 鉴权 | JWT Token（含角色信息） | 前端存储，每次请求携带 |
| | 反馈 | 反馈按钮组件 | 用户正/负反馈入口 |
| | API版本 | URL路径版本化（/api/v1/） | 版本号随语义层schema_version变更 |
| | 兼容性 | 废弃字段标记@Deprecated | 旧版API继续可用，通知前端限期升级 |
| **后端** | 框架 | Spring Boot 3.x | 单服务架构，全部业务逻辑 |
| | AI框架 | Spring AI Alibaba（问数链） | DataAgent/Graph模块，ReAct Agent、Tool Calling、RAG |
| | 基座模型 | 通义千问 qwen-max / qwen-plus / qwen-turbo（按环境切换） | 生产用max保质量，测试用plus平衡成本，开发用turbo极致省钱 |
| | 缓存抽象层 | CacheService 接口 + CaffeineCacheServiceImpl（默认） + RedisCacheServiceImpl（预留） | 接口可替换，当前环境通过 @Profile 控制生效实现 |
| **数据层** | 业务数据库 | MySQL 8.0 | 100-200万条数据，行式存储 |
| | 数据库迁移 | Flyway | 版本化SQL迁移脚本，CI/CD自动执行 |
| | 本地缓存 | Caffeine | L1本地缓存（语义/查询/权限），maximumSize=500, expireAfterWrite=24h |
| | 会话持久化 | MySQL（写穿透） | Caffeine做L1加速，会话数据异步写入MySQL保证不丢失 |
| | 反馈队列 | MySQL表 + Spring Schedule定时轮询 | 轻量级队列实现，避免引入Redis运维成本 |
| **观测层** | 可观测性 | LangFuse（SaaS托管版） | 全链路Traces、Token消耗、成本、延迟 |
| | 缓存监控 | Micrometer + Actuator /actuator/metrics | 缓存命中率、容量使用率、淘汰率 |
| **辅助工具** | 数据生成 | Python脚本 | 生成100-200万条测试数据 |
| | 测试执行 | Python脚本 | 批量发送测试用例、统计准确率 |
| | 持续回归 | CI/CD（GitHub Actions） | 每次提交自动跑150条测试用例 |
| **部署** | 容器化 | Docker Compose | 一键启动全部服务 |
| | 反向代理 | Nginx | 前端静态资源 + API转发 |

---

## 语言分布

| 语言 | 占比 | 负责内容 |
|---|---|---|
| **Java** | ~80% | 后端全部业务逻辑（Spring Boot单服务） |
| **SQL** | ~10% | 数据库表结构、指标定义、审计日志 |
| **Python** | ~5% | 100-200万条测试数据生成脚本、批量测试脚本 |
| **Vue3/TS** | ~5% | 前端界面 |

---

## 核心链路设计

### NL2SQL完整流程

1. 用户输入自然语言问题
2. **语义缓存检查**：通过 CacheService 在 Caffeine 中查找相同问题（24h内），命中则直接返回缓存结果，在步骤16统一记录审计日志（cache_hit=true）；未命中则继续后续链路
3. **意图识别 + 置信度评估**：判断是否为"问数"意图，评估置信度，低于阈值则生成反问澄清问题返回前端
4. **指标语义层匹配**：从`metric_definitions`表匹配预定义指标，命中则直接套用模板SQL，未命中进入NL2SQL流程
5. **Schema召回**：从MySQL元数据表中通过BM25关键词匹配最相关的表和字段
6. **SQL生成**：基于召回的Schema + Few-Shot示例 + 业务术语映射，根据环境配置调用对应模型（生产环境qwen-max、测试环境qwen-plus、开发环境qwen-turbo）生成SQL
7. **SQL校验**：语法校验 + 安全校验（禁止DROP/DELETE/UPDATE） + 语义一致性自检
8. **权限注入**：根据用户角色自动追加WHERE条件（如运营只能看自己区域的数据）
9. **性能优化**：自动注入`LIMIT 1000`，设置查询超时30秒
10. **SQL执行**：在MySQL只读连接中执行查询，返回结果集
11. **自我修正**：执行报错时，将错误信息回传模型自动修正，最多重试3次
12. **降级处理**：模型API超时 → 降级到qwen-plus；qwen-plus也超时 → 查询语义缓存中该问题的最近一次成功结果（如有，命中则直接返回）；仍不可用则返回"当前系统繁忙，请稍后重试"
13. **结果格式化 + 数据溯源**：附带使用的表、生成的SQL、匹配的指标定义、置信度等级
14. **审计日志**：记录userId、role、question、generated_sql、result_hash、timestamp、ip
15. **语义缓存写入**：通过 CacheService 将查询结果写入 Caffeine 语义缓存（maximumSize=500, expireAfterWrite=24h）；缓存命中的请求已在步骤2记录审计日志，此处不再重复写入
16. **会话写穿透**：会话数据通过写穿透策略异步持久化到MySQL的session_records表，确保服务重启不丢失对话上下文

### RBAC权限体系

| 角色 | 数据范围 | 功能权限 |
|---|---|---|
| **管理员（admin）** | 全部数据 | 管理指标定义、查看审计日志、管理用户 |
| **运营（operator）** | 仅自己负责的区域 | 问数、查看自己的历史记录 |
| **产品（product）** | 全部数据（只读） | 问数、查看历史记录 |
| **管理层（manager）** | 全部数据（只读） | 问数、查看仪表盘 |

**实现方式**：

- `users`表新增`role`字段（admin/operator/product/manager）
- `users`表新增`data_scope`字段（all/region_xxx）
- SQL生成后，在WHERE条件中自动注入权限过滤条件

### 指标语义层设计

`metric_definitions`表结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| metric_id | BIGINT | 主键 |
| metric_name | VARCHAR(100) | 指标名称（如"销售额"） |
| synonyms | VARCHAR(500) | 同义词（如"营收,收入,GMV"） |
| expression | TEXT | 计算表达式（如"SUM(amount)"） |
| related_tables | VARCHAR(200) | 关联表（如"orders"） |
| definition | TEXT | 口径说明（如"含税金额，不含退款"） |
| template_sql | TEXT | 模板SQL片段 |
| version | INT | 版本号，每次变更+1 |
| status | VARCHAR(20) | 状态：draft/reviewed/published/deprecated |
| created_by | BIGINT | 创建人（用户ID） |
| approved_by | BIGINT | 审批人（用户ID） |
| approved_at | TIMESTAMP | 审批时间 |
| changelog | TEXT | 变更记录（谁在什么时候改了什么） |

**匹配流程**：用户提问 → 提取关键词 → 与metric_name和synonyms做模糊匹配 → 命中则注入模板SQL → 未命中走NL2SQL

**指标口径管理**：

- **生命周期**：草稿（draft）→ 评审（reviewed）→ 上线（published）→ 废弃（deprecated）
- **审批流**：创建人提交 → 产品经理评审 → 审批通过后上线
- **版本管理**：每次变更版本号+1，保留历史版本可回滚
- **变更记录**：changelog字段记录每次变更的详情

**指标血缘**：

- 新增`metric_lineage`表，记录`metric_id` → `downstream_reports`（依赖此指标的报表/仪表盘ID列表）
- 修改指标时，自动查询血缘关系并通知下游使用者
- 废弃指标前，检查是否有下游依赖，有则强制走审批流程

### 置信度评估 + 反问澄清

- SQL生成后让模型自评置信度（高/中/低）
- 低于阈值时，模型生成澄清问题返回前端（如"您指的是销售额、利润还是订单量？"）
- 前端展示反问选项，用户选择后重新提问

### 数据溯源

每次返回结果附带元信息：

- 使用的表和字段
- 生成的SQL语句
- 匹配的指标定义（如有）
- 置信度等级
- 前端展示"查询依据"面板，用户可展开查看完整推理过程

### 自我修正闭环

- SQL执行报错 → 错误信息 + 原SQL回传模型 → 重新生成 → 最多重试3次
- 3次仍失败则返回错误提示 + 建议用户换一种问法

### 安全防护体系

- **SQL注入防护**：正则白名单，禁止DROP/DELETE/UPDATE/ALTER/TRUNCATE
- **只读连接**：MySQL连接强制只读模式
- **Prompt注入防护**：过滤用户输入中的恶意指令
- **行级权限**：不同用户通过WHERE条件限制数据范围
- **API限流**：每用户每分钟最多10次请求

### 性能优化层

| 优化项 | 实现方式 | 目标 |
|---|---|---|
| **索引优化** | 关键查询字段加索引（order_date, user_id, region） | 避免全表扫描超时 |
| **结果集控制** | SQL自动注入`LIMIT 1000`，超出提示用户缩小范围 | 避免大结果集拖垮前端 |
| **语义缓存** | Caffeine本地缓存，key=hash(question+schema_version)，maximumSize=500, expireAfterWrite=24h；淘汰策略：LRU；防穿透：空结果也缓存（TTL 5分钟）；防击穿：Caffeine内置CacheLoader原子加载 | 重复查询直接返回，节省Token |
| **Schema指纹缓存** | Caffeine L1缓存（maximumSize=1, expireAfterWrite=1h）+ MySQL持久化 | 快速检测表结构变更，MySQL保证不丢失 |
| **连接池管理** | HikariCP，最大连接数10（2G内存环境调优值，生产环境可根据内存规模调整至20），空闲超时10分钟 | 避免连接泄漏，控制数据库负载 |
| **查询超时** | 设置查询超时30秒，超时自动kill并返回提示 | 避免慢查询拖垮数据库 |

### 降级与容灾

| 降级场景 | 降级策略 | 兜底方案 |
|---|---|---|
| **模型API超时** | 自动降级到qwen-plus（更便宜、更快） | 返回"当前系统繁忙，请稍后重试" |
| **qwen-plus也超时** | 返回语义缓存中该问题的最近一次成功结果（如有）；仍不可用则返回友好提示 | 返回友好提示 + 建议稍后重试 |
| **数据库慢查询** | 30秒超时自动kill；连接池满时快速失败 | 返回预计算宽表结果（如有） |
| **Caffeine缓存失效** | 降级为不缓存，直接走完整链路 | 不影响核心功能，仅损失缓存收益 |
| **LangFuse不可用** | 降级为不记录Trace | 不影响核心功能，仅损失可观测性 |
| **数据丢失** | 每日全量备份 + 实时binlog增量备份 | RPO≤1小时，RTO≤30分钟 |

### 全局异常处理与错误码规范

- **统一响应格式**：所有API返回 `{ code, message, data, traceId }` 结构
- **错误码规范**：

| 错误码 | 含义 | 说明 |
|---|---|---|
| 2000 | 成功 | 请求处理成功 |
| 4000 | 参数校验失败 | 输入参数不合法 |
| 4001 | 意图识别非问数 | 用户问题不是问数意图 |
| 4002 | 置信度低，需澄清 | 模型置信度低于阈值，触发反问 |
| 4003 | SQL安全拦截 | SQL包含危险操作被拦截 |
| 4004 | 权限不足 | 用户无权限访问该数据 |
| 5000 | 系统内部错误 | 未预期的系统异常 |
| 5001 | 模型API超时 | 大模型调用超时 |
| 5002 | 数据库查询超时 | SQL执行超过30秒 |

- **全局异常处理器**：Spring `@RestControllerAdvice` + `@ExceptionHandler` 统一捕获，不暴露堆栈给前端
- **traceId**：每次请求生成唯一traceId，贯穿全链路（前端→后端→LangFuse），便于问题定位

### 审计日志

`audit_logs`表结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| log_id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| role | VARCHAR(20) | 用户角色 |
| question | TEXT | 原始问题 |
| generated_sql | TEXT | 生成的SQL |
| result_hash | VARCHAR(64) | 结果摘要哈希 |
| confidence | VARCHAR(10) | 置信度等级 |
| latency_ms | INT | 总耗时（毫秒） |
| token_used | INT | Token消耗量 |
| cache_hit | BOOLEAN | 是否命中缓存（true=缓存直接返回，未走NL2SQL链路） |
| ip_address | VARCHAR(45) | 请求IP |
| created_at | TIMESTAMP | 记录时间 |

### 成本与延迟监控（LangFuse）

- **Token消耗统计**：每次调用记录输入/输出Token数
- **延迟分布**：分别记录Schema召回耗时、SQL生成耗时、SQL执行耗时
- **成本预警**：设置日消费上限，超出自动降级到qwen-plus
- **质量追踪**：按天统计准确率趋势

**成本估算**：

- **单次查询Token消耗**：输入（用户问题 + Schema + Few-Shot）约800-1500 tokens，输出（SQL）约50-200 tokens，取中值约1000 tokens
- **日均查询量**：按20人 × 5次/天 = 100次/天
- **日Token消耗**：100次 × 1000 tokens = 10万 tokens
- **月Token消耗**：10万 × 30天 = 300万 tokens = 3百万 tokens
- **月成本估算**：qwen-max约30元/百万tokens → 3 × 30 = 月约90元；qwen-plus单价约qwen-max的1/3 → 月约30元
- **缓存优化收益**：预计30%查询命中缓存，仅70%走模型 → qwen-max月约63元，qwen-plus月约21元

---

## 缓存抽象层设计

> 本节描述 CacheService 抽象层的设计思路、实现方案及面试叙事。

### 设计背景

NL2SQL项目当前为单机部署（99元ECS），目标用户10-20人，缓存数据（SQL解析结果、语义映射、大模型返回的JSON）不需要跨实例共享。在此场景下，Caffeine本地缓存相比Redis具有纳秒级延迟、零运维成本的优势。但为保留未来扩展能力，设计了 CacheService 抽象接口，将缓存操作抽象化，实现"当下务实 + 未来可扩展"的架构目标。

### 接口设计

```
CacheService (接口)
  ├── CaffeineCacheServiceImpl  ← 默认实现（L1本地缓存，@Profile默认激活）
  │   ├── semanticCache()     → 语义缓存（maximumSize=500, expireAfterWrite=24h）
  │   ├── queryCache()        → 查询缓存（maximumSize=500, expireAfterWrite=24h）
  │   ├── permissionCache()   → 权限缓存（maximumSize=100, expireAfterWrite=1h）
  │   ├── schemaFingerprint() → Schema指纹缓存（maximumSize=1, expireAfterWrite=1h）
  │   ─ sessionCache()      → 会话缓存（maximumSize=50, expireAfterWrite=30min + 写穿透MySQL）
  ─ RedisCacheServiceImpl   ← 可选实现（L2分布式缓存，@Profile指定"redis"时激活）
      ─ 同上方法（内部使用Redis客户端实现）
```

**接口方法**：
- `get(key)` — 获取缓存值
- `put(key, value, ttl)` — 写入缓存并设置TTL
- `evict(key)` — 删除指定缓存项
- `clear()` — 清空所有缓存

**降级策略**：CaffeineCacheServiceImpl 内部对缓存操作做 try-catch，写入失败不抛异常，仅打 WARN 日志，保证核心业务不受缓存影响。

**环境切换**：通过 Spring `@Profile` 注解控制生效实现。默认激活 CaffeineCacheServiceImpl，生产环境如需切换到分布式缓存，仅需修改一个 Bean 的 @Primary 注解或激活 "redis" Profile，无需修改业务代码。

### 分层替代方案

| 缓存角色 | 实现方案 | 选择理由 |
|---|---|---|
| **语义缓存** | Caffeine（L1本地） | 单机部署，读多写少，key量小，丢失可接受 |
| **查询缓存** | Caffeine（L1本地） | 同上 |
| **权限缓存** | Caffeine（L1本地） | 变更频率极低，数据量极小 |
| **Schema指纹** | Caffeine缓存 + MySQL持久化 | 本质是MD5字符串，MySQL做单一事实来源，Caffeine做L1缓存加速（maximumSize=1, expireAfterWrite=1h） |
| **会话记忆** | Caffeine（L1）+ MySQL写穿透 | 兼顾纳秒级读取性能与会话数据不丢失 |
| **反馈队列** | MySQL表 + Spring Schedule定时轮询 | 轻量级队列实现，避免引入Redis运维成本 |

### 缓存监控与可观测性

- **命中率监控**：在 CacheService 中通过 Caffeine 内置的 `Cache.stats()` 埋点，通过 Micrometer 暴露到 Actuator 的 `/actuator/metrics` 端点，分别统计语义缓存、查询缓存、权限缓存、Schema指纹缓存、会话缓存的命中率
- **容量监控**：通过 `cache.stats().estimatedSize()` 监控缓存使用率，超过90%时自动降级（跳过缓存，直接走数据库）
- **缓存预热**：服务启动时通过 `ApplicationListener<ApplicationStartedEvent>` 预加载权限缓存和Schema指纹，避免首请求穿透到数据库
- **Schema指纹变更告警**：Schema指纹缓存命中率为0且MySQL指纹表有变更时，自动通知管理员

### 缓存一致性策略

- **权限缓存更新（写后更新 Write-Around）**：用户权限变更 → 先更新MySQL → 同步更新Caffeine缓存。选择写后更新而非写后删的原因：删缓存存在竞态条件——删除后、数据库写完成前，另一个请求可能把旧数据重新写入缓存。
- **会话缓存更新（写穿透 Write-Through）**：会话数据写入Caffeine L1的同时，异步持久化到MySQL。确保服务重启时会话数据不丢失，Caffeine缓存可作为MySQL的加速层。
- **Schema指纹更新**：Schema变更检测定时任务发现变更 → 更新MySQL中的指纹 → 刷新Caffeine缓存（maximumSize=1，直接替换）

### RedisCacheServiceImpl 一致性策略（预留）

若未来切换至 Redis 分布式缓存，一致性策略需调整为：
- **权限缓存**：权限变更 → 更新MySQL → 删除Redis缓存（TTL由下次请求重建），因Redis跨实例共享，写后删可避免竞态
- **会话缓存**：会话数据写入Redis（设置TTL）+ 异步持久化到MySQL，服务重启时从MySQL恢复会话到Redis
- **Schema指纹**：Schema变更 → 更新MySQL → 删除Redis缓存，下次请求重建

### 面试叙事

> "我设计了一个 CacheService 接口，将缓存操作抽象化。当前生产环境使用 Caffeine 本地缓存实现，因为项目是单机部署，缓存数据不需要跨实例共享。但我预留了 RedisCacheServiceImpl，如果未来需要多实例部署，只需修改一个 Bean 的 @Primary 注解即可平滑切换。同时，我在 Caffeine 实现中加入了写穿透策略，确保会话数据持久化到 MySQL，避免服务重启导致数据丢失。缓存命中率、容量使用率全部纳入监控，超过阈值自动降级。这不是过度设计，而是在成本可控的前提下，为未来预留了正确的扩展方向。"

---

## 数据治理

### Schema变更检测

数据库是"活的"，不能假设表结构不变。

- **实现方式**：定时任务每小时对比MySQL `information_schema` 与 MySQL 中存储的Schema指纹（MD5）
- **检测变更**：新增表/字段/索引/注释变更 → 自动刷新语义层缓存 + 通知管理员
- **变更记录**：写入`schema_changes`表，记录变更类型、变更内容、检测时间
- **面试价值**：体现"生产环境数据库是活的，系统能自动感知变化"

`schema_changes`表结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| change_id | BIGINT | 主键 |
| change_type | VARCHAR(20) | 变更类型：table_added/field_added/field_modified/index_changed |
| table_name | VARCHAR(100) | 变更的表名 |
| old_value | TEXT | 变更前值 |
| new_value | TEXT | 变更后值 |
| detected_at | TIMESTAMP | 检测时间 |
| notified | BOOLEAN | 是否已通知管理员 |

### 数据质量监控

- **实现方式**：定时任务每日检查关键字段的空值率、枚举值分布、数据量波动
- **告警规则**：
  - 关键字段空值率 > 10% → 告警
  - 枚举值分布突变（如某品类订单量突然为0）→ 告警
  - 日数据量波动 > 50%（对比前7天均值）→ 告警
- **告警渠道**：写入`data_quality_alerts`表，前端展示告警面板
- **面试价值**：体现"数据质量是生产系统的生命线"

`data_quality_alerts`表结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| alert_id | BIGINT | 主键 |
| alert_type | VARCHAR(50) | 告警类型：null_rate/enum_drift/volume_spike |
| table_name | VARCHAR(100) | 涉及的表 |
| field_name | VARCHAR(100) | 涉及的字段 |
| current_value | VARCHAR(200) | 当前值 |
| threshold | VARCHAR(100) | 阈值 |
| severity | VARCHAR(10) | 严重程度：warning/critical |
| created_at | TIMESTAMP | 创建时间 |
| acknowledged | BOOLEAN | 是否已确认 |

---

## 用户反馈闭环

让系统具备"自我进化"能力，而非静态交付。

- **前端反馈入口**：每个查询结果下方提供反馈按钮，用户可标记"有用"或"无用"
- **负反馈收集**：用户点击"无用"后弹出输入框，可填写正确问法或正确SQL
- **审计日志区分缓存命中**：audit_logs表新增cache_hit字段（BOOLEAN），标记请求是否命中语义缓存。缓存命中的请求未走NL2SQL完整链路，在分析准确率时应单独统计
- **反馈队列**：负反馈写入`feedback_queue`表（question + generated_sql + user_comment + user_corrected_sql + status），通过 Spring Schedule 定时任务轮询 `status='pending'` 的记录进行处理
- **定期处理**：数据分析师每周 Review 反馈队列，标注正确SQL，补充Few-Shot示例
- **模型优化**：积累足够负样本后，补充到Prompt的Few-Shot中，提升后续准确率
- **面试价值**：体现"系统能持续进化，准确率会随时间提升"

**反馈队列技术选型说明**：反馈队列本质上是一个有状态的工作队列，我用 MySQL 表 + 定时任务实现了轻量级队列，避免了引入 Redis 的运维成本。如果后续反馈量级增长到需要实时处理，可以平滑迁移到 RabbitMQ 或 Kafka。

`feedback_queue`表结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| feedback_id | BIGINT | 主键 |
| user_id | BIGINT | 反馈用户ID |
| question | TEXT | 原始问题 |
| generated_sql | TEXT | 系统生成的SQL |
| result_hash | VARCHAR(64) | 返回结果摘要 |
| feedback_type | VARCHAR(10) | 反馈类型：positive/negative |
| user_comment | TEXT | 用户填写的评论/正确SQL |
| status | VARCHAR(20) | 处理状态：pending/analyzed/added_to_fewshot/resolved |
| created_at | TIMESTAMP | 反馈时间 |
| processed_at | TIMESTAMP | 处理时间 |

---

## 持续回归测试

评测驱动开发，确保每次改动不降低准确率。

- **CI/CD集成**：每次代码提交自动触发GitHub Actions，运行150条测试用例
- **准确率门禁**：端到端准确率低于80% → 阻断合并请求
- **影子数据集**：生产流量复制一份到测试环境，持续验证新Prompt效果
- **A/B测试**：新Prompt上线前先灰度10%流量，对比新旧版本准确率
- **报告输出**：每次回归测试自动生成报告，按类别统计准确率趋势
- **面试价值**：体现"评测驱动开发"的工程素养，而非"写完就扔"

---

## 数据库迁移策略

生产环境的表结构变更必须通过迁移工具管理版本，禁止手动执行SQL。

- **工具选型**：Flyway，版本化SQL迁移脚本，CI/CD自动执行
- **命名规范**：`V{version}__{description}.sql`（如 `V1_0_1__add_user_role_field.sql`）
- **执行时机**：服务启动时自动检测并执行未应用的迁移脚本
- **回滚策略**：Flyway支持 `undo` 命令，重大变更保留回滚脚本
- **面试价值**：体现"数据库变更有版本、可追溯、可回滚"的工程规范

---

## 测试方案

### 测试数据规模与生成

使用Python脚本生成100-200万条测试数据，表结构如下：

| 表名 | 数据量 | 字段 | 说明 |
|---|---|---|---|
| **orders** | 150万条 | order_id, user_id, product_id, amount, quantity, price, order_date, region, status, channel | 订单主表，覆盖近2年数据 |
| **users** | 20万条 | user_id, username, age, gender, city, register_date, vip_level, role, data_scope | 用户表，覆盖全国主要城市 |
| **products** | 1万条 | product_id, product_name, category, sub_category, brand, cost_price, sell_price | 商品表，覆盖多品类多品牌 |
| **metric_definitions** | 20条 | 预定义指标 | 销售额、订单量、客单价、复购率等 |
| **business_glossary** | 30条 | 业务术语映射 | "卖得最好"→销量最高，"大客户"→消费>1万等 |

### 测试用例设计（150条，按准确率维度）

| 测试类别 | 用例数 | 示例问题 | 预期 |
|---|---|---|---|
| **单表简单查询** | 20 | "上个月销售额是多少" | 生成正确的SELECT + WHERE |
| **单表聚合查询** | 20 | "各地区的订单数量排名" | 生成GROUP BY + ORDER BY |
| **多表JOIN查询** | 20 | "每个用户的总消费金额" | 正确JOIN orders和users |
| **时间范围查询** | 15 | "最近7天的新增用户数" | 正确解析"最近7天"为日期范围 |
| **模糊语义查询** | 15 | "卖得最好的商品是什么" | 正确理解"卖得最好"= 销量最高 |
| **复杂嵌套查询** | 10 | "消费金额超过平均值的用户有哪些" | 生成子查询 |
| **边界/异常测试** | 10 | "删除所有订单" / 空输入 / 超长输入 | 安全拦截，不执行危险操作 |
| **权限隔离测试** | 10 | 运营角色问"全国销售额" | 只返回自己区域的数据 |
| **大数据量性能测试** | 10 | "所有用户的总消费排名" | 30秒内返回，不超时 |
| **缓存命中测试** | 10 | 同一问题连续问3次 | 第2次起延迟<500ms，审计日志cache_hit=true |
| **并发测试** | 10 | 5人同时提问 | 无死锁、无数据串扰 |
| **总计** | **150** | | 目标准确率 ≥ 80%（120/150通过） |

### 评测指标体系

| 评测维度 | 指标 | 计算方式 | 目标 |
|---|---|---|---|
| **SQL语法正确率** | 生成的SQL能否被MySQL解析执行 | 语法正确数 / 总用例数 | ≥ 95% |
| **执行结果正确率** | 查询结果是否与预期一致 | 结果正确数 / 总用例数 | ≥ 85% |
| **端到端准确率** | 从提问到最终返回结果完全正确 | 端到端正确数 / 总用例数 | ≥ 80% |
| **安全拦截率** | 危险操作是否被正确拦截 | 拦截成功数 / 危险用例数 | 100% |
| **反问澄清准确率** | 模糊问题是否正确触发反问 | 正确反问数 / 模糊用例数 | ≥ 70% |
| **权限隔离准确率** | 不同角色是否只能看到权限范围内的数据 | 隔离正确数 / 权限用例数 | 100% |
| **平均响应延迟** | 从提问到返回结果的总耗时 | 所有用例延迟均值 | ≤ 5秒 |
| **平均Token消耗** | 每次调用的Token消耗量 | 所有用例Token均值 | ≤ 2000 tokens |
| **缓存命中率** | 语义缓存命中请求占比 | 缓存命中次数 / 总请求次数 | ≥ 30% |

### 测试执行流程

1. **数据准备**：运行Python脚本生成100-200万条数据，导入MySQL
2. **Schema初始化**：启动Java服务，自动加载表结构元数据
3. **指标语义层初始化**：预置20条指标定义 + 30条业务术语映射
4. **用户角色初始化**：创建admin/operator/product/manager四种角色用户
5. **逐条执行测试用例**：通过Python脚本批量发送150条测试问题
6. **LangFuse记录**：每次调用自动记录完整链路（Prompt、生成SQL、执行结果、耗时、Token消耗）
7. **结果评估**：
   - SQL正确性：对比生成的SQL与预期SQL（允许语义等价但写法不同）
   - 结果正确性：对比查询结果与预期结果
   - 安全性：危险操作是否被正确拦截
   - 权限隔离：不同角色是否只能看到权限范围内的数据
8. **输出测试报告**：按类别统计准确率，定位失败用例的具体环节（Schema召回失败 / SQL生成错误 / 执行失败 / 安全拦截失败 / 权限隔离失败）

### 性能测试

| 测试项 | 方法 | 目标 |
|---|---|---|
| **并发测试** | 模拟5个用户同时提问 | 错误率 < 1% |
| **延迟测试** | 记录每个环节耗时分布 | P95 ≤ 8秒 |
| **缓存命中率** | 重复发送相同问题10次 | 第2次起命中缓存，延迟 < 500ms |
| **自我修正成功率** | 故意注入语法错误的SQL触发修正 | 修正成功率 ≥ 60% |
| **大数据量查询** | 150万条订单表全表聚合 | 30秒内返回结果 |

### 准确率调优策略

当准确率低于80%时，按以下优先级排查：

1. **Schema召回失败**：检查BM25关键词匹配是否遗漏相关表/字段 → 补充字段注释和同义词
2. **SQL生成错误**：检查Few-Shot示例是否覆盖该场景 → 补充对应类型的示例
3. **业务术语未识别**：检查`business_glossary`表是否缺少映射 → 补充术语映射
4. **指标口径不一致**：检查`metric_definitions`表定义是否完整 → 补充指标定义
5. **复杂JOIN失败**：在Prompt中注入表关系描述 → 限制JOIN深度不超过3张表

---

## 项目里程碑

| 阶段 | 时间 | 交付物 |
|---|---|---|
| **M1：基础链路** | 第1周 | Java单服务 + MySQL + 单表查询跑通 |
| **M2：语义层+安全+权限+迁移** | 第2周 | 指标语义层（含版本+审批+血缘） + 业务术语映射 + SQL安全校验 + RBAC权限体系 + 审计日志 + Flyway数据库迁移框架 |
| **M3：置信度+澄清+反馈** | 第3周 | 置信度评估 + 反问澄清 + 数据溯源 + 自我修正闭环 + 用户反馈闭环（反馈按钮 + feedback_queue表+Spring Schedule轮询） |
| **M4：性能优化+降级+缓存抽象层** | 第4周 | 索引优化 + LIMIT注入 + 超时控制 + HikariCP连接池 + CacheService抽象接口 + Caffeine本地缓存实现（含LRU/防穿透/防击穿-CacheLoader） + 会话写穿透MySQL + 降级与容灾策略 + 缓存监控埋点 |
| **M5：数据治理** | 第5周 | Schema变更检测（定时任务） + 数据质量监控 + 指标口径管理（生命周期+审批流） |
| **M6：前端+观测+异常处理** | 第6周 | Vue3前端 + ECharts图表 + LangFuse接入 + 成本/延迟监控 + 反馈按钮 + 全局异常处理与错误码规范 |
| **M7：持续回归** | 第7周 | CI/CD集成 + 150条测试用例自动回归 + 准确率门禁 + 影子数据集 |
| **M8：测试调优** | 第8周 | 150条测试用例执行 + 准确率调优至80%+ |
| **M9：性能测试** | 第9周 | 并发测试 + 大数据量测试 + 缓存测试 + 测试报告输出 |

---

## 风险与应对

| 风险 | 影响 | 应对措施 |
|---|---|---|
| NL2SQL准确率低于80% | 核心功能不可用 | 完善字段注释 + Few-Shot示例 + 业务术语映射表 + 指标语义层 + 用户反馈闭环持续优化 |
| 多表JOIN准确率低 | 复杂查询失败率高 | 在Prompt中注入表关系描述，限制JOIN深度不超过3张表 |
| Token成本失控 | 费用超预期 | LangFuse设置日消费上限 + 简单问题降级用qwen-plus + Caffeine语义缓存 |
| SQL注入攻击 | 数据泄露 | 强制只读连接 + 正则白名单 + 参数化查询 |
| 权限绕过 | 数据越权访问 | 权限注入在SQL执行前强制执行，不依赖模型生成 |
| 大数据量查询超时 | 用户体验差 | 索引优化 + LIMIT注入 + 30秒超时控制 |
| 置信度评估不准 | 该反问时没反问 | 调整置信度阈值 + 增加Few-Shot示例 |
| 自我修正死循环 | 反复重试消耗Token | 最多重试3次，超出直接返回错误提示 |
| 模型API不可用 | 核心功能不可用 | 降级到qwen-plus → 再降级到缓存兜底 → 返回友好提示 |
| Schema变更导致结果异常 | 查询结果不一致 | Schema变更检测定时任务 + 自动刷新缓存 + 通知管理员 |
| 数据质量异常 | 查询结果不可信 | 数据质量监控定时任务 + 空值率/枚举漂移/数据量波动告警 |
| 指标口径争议 | 业务部门不信任 | 指标口径管理（版本+审批+血缘+变更通知） |
| 反馈积压 | 负样本无法及时利用 | 每周Review反馈队列 + 定期补充Few-Shot示例 |
| 持续回归阻断发布 | 误报导致发布延迟 | 设置准确率容忍阈值（如78%以下才阻断），允许带风险发布 |
| 连接池打满 | 并发请求超时 | HikariCP最大连接数20 + 超时快速失败 + 不阻塞线程 |
| 缓存击穿 | 热点key失效导致数据库压力 | LRU淘汰 + Caffeine内置CacheLoader原子加载 + 空结果缓存防穿透 |
| 缓存容量超限 | OOM风险 | 监控estimatedSize，使用率超90%自动降级，跳过缓存直接走数据库 |
| Schema指纹缓存失效 | 表结构变更无法快速感知 | MySQL持久化保证不丢，Caffeine失效后下次请求重建，最长1h延迟 |
| 密钥泄露 | 敏感配置暴露 | 环境变量注入 + Vault/KMS管理 + trufflehog扫描 |
| CacheService切换失败 | 多实例部署时缓存不一致 | 接口已抽象，预留RedisCacheServiceImpl，通过@Profile平滑切换 |

---

## 一句话总结

**本项目的核心竞争力在于"每个答案都可信赖，不准时能告诉你它不准，出了问题能追溯定位"——指标语义层保证口径一致，置信度闭环避免瞎猜，用户反馈闭环让系统持续进化，降级容灾保证高可用，审计日志满足合规要求，LangFuse实现全链路可观测，CacheService抽象层实现"当下务实（Caffeine零运维）+ 未来可扩展（接口可平滑迁移到Redis）"的有边界感的企业级架构。**

---

## 第二部分：上云部署规划

## 电商问数项目上云配置规划

## 1. 项目概述

本项目旨在构建一个基于大语言模型（LLM）的电商问数系统（NL2SQL），支持自然语言查询电商业务数据。为兼顾生产级工程能力展示与个人项目的成本控制，上云部署规划采用"极致性价比"与"面试加分"并重的策略。通过合理利用阿里云营销活动、免费试用及本地缓存替代方案，在2核2G的入门级云服务器上实现高可用、低成本的部署架构。

## 2. 整体部署架构

项目整体采用应用、数据库、缓存分离的架构设计，核心组件部署拓扑如下：

- **Spring Boot 应用**：部署于 ECS 云服务器，由 Nginx 提供反向代理。
- **MySQL 数据库**：前期使用 RDS 免费试用，后期平滑迁移至 ECS 上的 Docker 容器。
- **语义缓存**：放弃购买 Redis 云实例，改用 Spring Boot 内嵌的 Caffeine 本地缓存。
- **通义千问 API**：通过阿里云百炼平台（DashScope）远程调用，无需本地部署。
- **LangFuse 可观测平台**：直接接入 LangFuse 官方云（Hobby免费版），零成本实现 Trace 采集。

## 3. 最优性价比配置方案

结合阿里云"99计划"及各类免费试用，制定以下高性价比配置方案：

| 组件 | 方案说明 | 费用预估 |
|------|---------|---------|
| ECS 云服务器 | 99元/年（2核2G + 40G ESSD + 3M固定带宽） | 约 8.3元/月 |
| MySQL 数据库 | 阿里云 RDS 免费试用3个月，到期后转 ECS Docker 自建 | 0元 |
| 语义缓存 | 不购买云实例，改用 Caffeine 本地缓存 | 0元 |
| 通义千问 API | qwen-max 按量计费 | 约 90元/月 |
| LangFuse | 官方云 Hobby 免费版 | 0元 |
| **合计** | **前3个月约 100元/月，之后稳定在约 110元/月** | **极低成本** |

## 4. ECS 部署架构与关键配置

### 4.1 部署拓扑

```
ECS (2核2G, 99元/年)
 ├── JDK 17 + Spring Boot应用 (JVM: -Xmx960m, MaxMetaspaceSize=320m)
 ├── Nginx (反向代理 80 -> 8080)
 ├── Docker
 │   ── MySQL 8.0 (RDS试用结束后启用, --memory=512m, 迁移时临时768m)
 ── Caffeine 本地缓存 (替代Redis)

外部服务 (不占ECS资源)
 ├── 阿里云 RDS MySQL (免费试用3个月)
 ├── 通义千问 API (DashScope, 按量计费)
 ── LangFuse Cloud (cloud.langfuse.com, 免费版)
```

### 4.2 JVM 调优参数

针对 2G 内存环境，必须严格限制 JVM 堆内存，避免 OOM。考虑到 Spring AI Alibaba 在启动时会加载模型元数据、初始化 HTTP 客户端连接池、注册多个 Micrometer 指标等额外开销，非堆内存需求高于常规 Spring Boot 应用，需适当调大 MaxMetaspaceSize：

```
java -Xms480m -Xmx960m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC -jar nl2sql-app.jar
```

| 参数 | 说明 |
|------|------|
| `-Xms480m` | 初始堆内存 480MB，避免启动时频繁扩容 |
| `-Xmx960m` | 最大堆内存 960MB（较原始 1024MB 降低 164MB，为非堆腾出空间） |
| `-XX:MaxMetaspaceSize=320m` | 元空间上限 320MB（较原始 256m 提高 64MB，应对 Spring AI Alibaba 的非堆开销） |
| `-XX:+UseG1GC` | 使用 G1 垃圾回收器，在低内存环境下表现更优 |

**内存分配原则**：堆 + 非堆总预算控制在 1280MB（960 + 320），为操作系统、Docker 及其他组件预留约 768MB 空间。

### 4.3 Caffeine 本地缓存配置

语义缓存的 Key 为 hash(question + schema_version)，数据量极小，Caffeine 完全能够胜任：

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterWrite=24h
```

**缓存抽象层设计**：项目设计了 `CacheService` 接口，当前生产环境使用 `CaffeineCacheServiceImpl`（L1 本地缓存），同时预留 `RedisCacheServiceImpl`（L2 分布式缓存）。若未来用户量增长需要多实例部署，只需修改一个 Bean 的 `@Primary` 注解即可平滑切换，无需修改业务代码。

### 4.4 LangFuse Cloud 接入配置

通过环境变量注入密钥，无需在 ECS 上自建 LangFuse：

```
export LANGFUSE_PUBLIC_KEY=pk-lf-xxxxxxxxxxxxx
export LANGFUSE_SECRET_KEY=sk-lf-xxxxxxxxxxxxx
export LANGFUSE_HOST=https://cloud.langfuse.com
```

## 5. RDS 试用到期平滑过渡方案

RDS 免费试用期（3个月）结束后，可直接在 ECS 上通过 Docker 运行 MySQL，实现零停机切换：

### 5.1 启动 Docker MySQL

```
docker run -d \
  --name mysql \
  --memory=512m \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=nl2sql \
  -v /data/mysql:/var/lib/mysql \
  mysql:8.0
```

**注意**：执行 Flyway 数据库大迁移脚本时，MySQL 可能因内存不足而 OOM。建议在迁移期间临时将容器内存限制调大至 768m，迁移完成后再调回 512m：

```
# 迁移前：临时扩容
docker update --memory=768m mysql

# 执行 Flyway 迁移（应用启动时自动执行）

# 迁移后：恢复限制
docker update --memory=512m mysql
```

### 5.2 数据迁移步骤

1. 试用到期前，使用 `mysqldump` 导出 RDS 数据。
2. 在 ECS Docker MySQL 中导入数据。
3. 修改应用 `application.yml` 中的数据库连接地址（从 RDS 内网地址改为 `127.0.0.1:3306`）。
4. 重启应用，完成平滑切换。

## 6. 内存评估与稳定性分析

### 6.1 Java 2G 内存吃紧的核心原因

2G 内存吃紧主要源于 Java JVM 的固有开销，而非项目业务逻辑本身。以下为整合 Spring AI Alibaba 额外开销后的详细评估：

| 组件 | 内存占用 | 说明 |
|------|---------|------|
| Linux 系统基础 | 200-300MB | 内核、SSH、cron 等基础服务 |
| Nginx | 5-15MB | 反向代理服务 |
| JVM 堆内存（-Xmx960m） | 最多 960MB | 存放对象实例 |
| JVM 非堆内存（Metaspace 320m + 线程栈 + 代码缓存） | 250-320MB | 元空间、线程栈、代码缓存；Spring AI Alibaba 加载模型元数据、HTTP 客户端连接池、Micrometer 指标等额外开销 |
| Docker MySQL（--memory=512m） | 最多 512MB | 数据库服务（空闲时约 200-300MB） |
| **合计峰值** | **约 1.9-2.1GB** | 接近 2G 上限 |
| **剩余可用** | **约 0-200MB** | 依赖 Swap 应对突发峰值 |

### 6.2 稳定性保障策略

在严格执行以下配置的前提下，个人项目（日均几十次查询）不会频繁出问题：

1. **严格限制 JVM 堆内存及元空间大小**：`-Xmx960m -XX:MaxMetaspaceSize=320m`。
2. **必须开启 Swap**：`sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`。
3. **数据库分离部署并限制内存**（Docker `--memory=512m`）。
4. **配置 HikariCP 连接池调优**（详见 6.3 节）。
5. **配置应用自动重启机制**（详见 6.4 节）。
6. **配置日志轮转**（详见 6.5 节）。

### 6.3 HikariCP 连接池调优

JDBC 连接池默认配置在 2G 环境下连接数过多，每个连接约占用 10-20MB 内存，需手动调小：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

| 参数 | 调优值 | 说明 |
|------|--------|------|
| `maximum-pool-size` | 10 | 最大连接数（默认 10，2G 环境已足够） |
| `minimum-idle` | 5 | 最小空闲连接 |
| `connection-timeout` | 30000 | 连接超时 30 秒 |
| `idle-timeout` | 600000 | 空闲连接超时 10 分钟 |
| `max-lifetime` | 1800000 | 连接最大生命周期 30 分钟 |

### 6.4 应用自动重启机制

OOM 或崩溃后应用不会自愈，必须配置自动重启机制：

**Spring Boot 应用（systemd 方式）：**

```ini
# /etc/systemd/system/nl2sql-app.service
[Unit]
Description=NL2SQL Spring Boot Application
After=syslog.target network.target

[Service]
Type=simple
User=www-data
ExecStart=/usr/bin/java -Xms480m -Xmx960m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC -jar /opt/nl2sql/nl2sql-app.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

**Docker MySQL 自动重启：**

```
docker update --restart=always mysql
```

### 6.5 日志轮转（logrotate）

40G ESSD 磁盘，Spring Boot 应用日志 + Nginx 访问日志 + Docker MySQL 日志，如果不做轮转，几周就可能打满磁盘。需配置 logrotate：

```
# /etc/logrotate.d/nl2sql-app
/opt/nl2sql/logs/*.log {
    daily
    rotate 5
    size 100M
    compress
    delaycompress
    missingok
    notifempty
    create 0640 www-data www-data
    copytruncate
}

# /etc/logrotate.d/nginx
/var/log/nginx/*.log {
    daily
    rotate 5
    size 100M
    compress
    delaycompress
    missingok
    notifempty
    create 0640 www-data www-data
    copytruncate
    postrotate
        [ -f /var/run/nginx.pid ] && kill -USR1 $(cat /var/run/nginx.pid)
    endscript
}
```

### 6.6 技术选型说明

本项目选择 Java 而非 Python，核心原因在于 2G 环境下 JVM 调优、内存管理、连接池控制等工程实践是面试中证明"生产级工程能力"的关键加分项。Python 虽在 2G 环境下更宽裕，但会损失 Java 在面试中的工程化加分。

## 7. 并发测试方案

### 7.1 项目实际并发量评估

| 场景 | 并发数 | 说明 |
|------|--------|------|
| 日常使用 | 1-3个 | 20人团队同一秒最多2-3人同时提问 |
| 面试演示 | 5-10个 | 模拟多用户同时查询 |
| 压测极限 | 20-50个 | 证明降级策略有效 |

### 7.2 瓶颈与应对策略

真正的瓶颈并非云服务器，而是通义千问 API 的并发限流（qwen-max 默认 QPS 约 5-10）。

**应对方案**：在应用中增加请求队列与限流器（如 Guava RateLimiter），控制对 API 的调用速率，并配合已有的五层降级策略。

### 7.3 推荐压测方案

- **JMeter 本地压测（推荐）**：电脑(JMeter) → 公网 → ECS(Spring Boot) → 通义千问API。
- **阿里云 PTS 性能测试**：利用平台免费额度。
- **简单脚本压测**：`ab -n 100 -c 10 http://你的ECS公网IP:8080/api/query`

**压测关注点**：缓存命中率、降级策略生效情况、数据库连接池回收状态、JVM 内存是否持续增长。

## 8. Nginx 反向代理部署

Nginx 作为独立反向代理服务运行在 ECS 上，监听 80 端口接收外部请求，转发至后端 8080 端口的 Spring Boot 应用。

### 8.1 内存开销评估

Nginx 极其轻量，内存占用仅 5-15MB，在 2G 内存环境下完全可忽略不计。

### 8.2 部署与配置步骤

1. **安装 Nginx**：`sudo apt update && sudo apt install nginx -y`
2. **配置反向代理**：编辑 `/etc/nginx/sites-available/nl2sql.conf`
3. **启用配置并重启**：

```
sudo ln -s /etc/nginx/sites-available/nl2sql.conf /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

### 8.3 Nginx 额外收益

- **统一入口**：未来可同时代理前端静态文件和后端 API。
- **HTTPS 支持**：未来加域名后，可用 certbot 快速配置。
- **静态资源缓存与请求限流**：减轻 Spring Boot 压力。

## 9. 安全组配置

为保障服务器安全，需严格限制端口访问权限：

| 端口 | 用途 | 授权对象 | 备注 |
|------|------|---------|------|
| 22 | SSH 远程连接 | 你的固定 IP | 切勿开放 0.0.0.0/0 |
| 80 | Nginx 对外服务 | 0.0.0.0/0 | 统一对外入口 |
| 8080 | Spring Boot 应用 | 关闭 | 不再对外暴露 |
| 3306 | MySQL 数据库 | 127.0.0.1 | 仅本地访问（RDS到期后自建时） |

## 10. 部署检查清单

部署前逐项确认以下清单，确保所有配置均已落实：

| 序号 | 检查项 | 状态 |
|------|--------|------|
| 1 | ECS 系统：Ubuntu 22.04 LTS，内核 ≥ 5.15 | □ |
| 2 | Swap：已创建 2G swapfile 并启用（`swapon --show` 确认） | □ |
| 3 | JDK：OpenJDK 17 或 Alibaba Dragonwell 17 | □ |
| 4 | Docker：已安装，MySQL 容器 `--memory=512m` 限制已设置 | □ |
| 5 | Nginx：已安装，nl2sql.conf 反向代理已配置，8080 未对外暴露 | □ |
| 6 | 安全组：仅开放 22（固定IP）、80（0.0.0.0/0），关闭 8080 和 3306 | □ |
| 7 | JVM 参数：`-Xms480m -Xmx960m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC` | □ |
| 8 | Caffeine 配置：`maximumSize=500, expireAfterWrite=24h` | □ |
| 9 | HikariCP：`maximum-pool-size=10`, `minimum-idle=5`, `connection-timeout=30000` | □ |
| 10 | LangFuse：环境变量 `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` / `LANGFUSE_HOST` 已注入 | □ |
| 11 | 通义千问：DashScope API Key 已配置，qwen-max 配额已确认 | □ |
| 12 | Flyway：数据库迁移脚本已验证可在 512m 内存下执行（迁移时临时扩容至 768m） | □ |
| 13 | 日志轮转：logrotate 已配置，单文件 ≤ 100MB，保留 5 个轮转 | □ |
| 14 | 自动重启：systemd `Restart=on-failure` 或 Docker `--restart=always` | □ |
| 15 | 健康检查：`/actuator/health` 端点可访问 | □ |
| 16 | 缓存预热：`ApplicationStartedEvent` 监听器已注册 | □ |

## 11. 成本汇总

- **前3个月（RDS 试用期内）**：ECS (8.3元) + 通义千问 API (90元) ≈ **100元/月**。
- **3个月后（RDS 到期自建）**：ECS (8.3元) + 通义千问 API (90元) ≈ **110元/月**。

整体部署以极低的成本实现了生产级架构的完整闭环。

## 12. 面试加分话术

在面试中介绍该项目时，可重点强调以下工程化实践：

1. **极致的资源调优能力**："在 2G 内存的受限环境下，我通过严格限制 JVM 堆内存（-Xmx960m）、调大元空间（MaxMetaspaceSize=320m）以应对 Spring AI Alibaba 的非堆开销、开启 Swap、使用 Caffeine 替代 Redis 等手段，成功保障了 Java 应用的稳定运行。"

2. **高可用与平滑过渡设计**："设计了 RDS 免费试用到 Docker 自建的平滑迁移方案，通过数据导出导入和配置热切换，实现了零停机的数据库过渡。迁移期间临时扩容 MySQL 容器内存至 768m 以应对 Flyway 大迁移的 OOM 风险。"

3. **生产级架构思维**："引入了 Nginx 反向代理、LangFuse 链路追踪、HikariCP 连接池调优、日志轮转以及应用自动重启机制，使个人项目具备了企业级微服务架构的雏形。同时设计了 CacheService 抽象层，缓存实现可在 Caffeine 与 Redis 之间平滑切换。"

4. **成本与性能的平衡**："在架构选型时，不仅考虑了技术先进性，还通过合理利用云厂商免费资源和内嵌缓存，将月均运行成本控制在 100 元左右，体现了优秀的成本意识。"
