# CLAUDE.md — NL2SQL 问数系统

> 本文件是项目级约定，优先级高于 AI 默认行为。每轮会话自动加载。

## 项目定位

个人级企业问数系统（NL2SQL）：自然语言提问 → 生成只读 SQL → 执行 → 返回结果，附带企业级可信度保障（RBAC / 指标语义层 / 置信度闭环 / 审计 / 降级容灾）。

## 工作方式（Spec Coding，最高优先级）

本仓库采用 Spec Coding。正式工程代码严格对齐 Spec，禁止脱离 Spec 自由发挥。

**必读文档（按角色）：**

| 文档 | 作用 | 何时读 |
|---|---|---|
| `1-NL2SQL项目章程_完整版.md` | PRD / 愿景（讲"做什么"） | 理解需求背景 |
| `SPEC_CODING_STANDARD_v2.md` | Spec 写作标准（讲"怎么写"） | 起草 / 审查任何 Spec 前 |
| `docs/decisions/ADR-*.md` | 人拍板的决策记录 | 遇歧义 / 冲突时，**以 ADR 为准** |
| `docs/spec/*.md` | 交付 Spec（讲"每种输入输出什么"） | 生成 / 修改代码前必读对应里程碑 |

## 硬约束（违反即回炉）

1. **行为变更顺序**：先改 Spec → 更新变更记录 → 再改代码。禁止代码与 Spec 各自漂移。
2. **代码回链**：实现处注释 / 提交信息引用 `// REQ-<模块号>-<流水>`。
3. **测试回链**：测试用例名 / 注释引用 `TC-<模块号>-<流水>-<序号>`。
4. **无编号代码视为脱离 Spec**，需补编号或回炉。
5. **冲突裁决**：ADR（人拍板） > Spec > 章程；ADR 未覆盖的，停下来问，不自行决定。
6. **JSON 字段命名（易漏，强制）**：对外接口请求/响应字段与 LLM 输出字段一律 **snake_case**；Java record/DTO 组件用 camelCase 时，**必须**加 `@JsonProperty("snake_case")` 显式桥接。凡 record/DTO 含下划线字段却漏了 `@JsonProperty`，即为漏映射 bug（M3 `is_query`/`feedback_type`、M5 `related_tables` 已各踩一次）。**新增任何 record/DTO 前先自查本条。**

## Git 操作约定

本仓库所有 git 操作由**用户手动执行**，AI **不执行任何 git 命令**（不 `add` / `commit` / `push` / `checkout`）。

- 到达提交点时，AI 输出**完整的 git 命令**（含 `git add` 文件清单 + 建议的 `git commit -m` 信息），供用户复制执行；
- commit message 遵循：`<类型>: <简述>`，并引用 REQ/TC 编号（例：`feat(m2): 语义层+RBAC+审计 // REQ-302~510`）；
- AI 不代为执行，也不在未经用户要求时改动 git 状态。

## 全局错误码速查

| 错误码 | 含义 | 启用 |
|---|---|---|
| 2000 | 成功 | M1 |
| 4000 | 参数校验失败 | M1 |
| 4001 | 意图识别非问数 | M3 |
| 4002 | 置信度低，需澄清 | M3 |
| 4003 | SQL 安全拦截 | M1 |
| 4004 | 权限不足 | M2 |
| 4290 | 并发超限 | M1 |
| 5000 | 系统内部错误（兜底） | M1 |
| 5001 | 模型 API 超时 | M1 |
| 5002 | 数据库查询超时 | M1 |
| 5003 | 上游返回格式异常 | M1 |

## 技术栈速查

- 构建：Maven；语言：Java 17；框架：Spring Boot 3.x
- 数据层：MySQL 8.0 + Flyway + HikariCP（`maximum-pool-size=10`）+ JdbcTemplate
- SQL 解析：JSqlParser（语法预解析）
- 模型：通义千问 qwen-turbo（dev，经 DashScope OpenAI 兼容端点，REST 直连；M3+ 再评估是否引入 Spring AI Alibaba）
- 统一响应：`{ code, message, data, traceId }`

## JSON 字段命名约定

对外接口契约（HTTP 请求/响应字段）与 LLM 输出字段均用 **snake_case**（如 `feedback_type`、`is_query`、`result_hash`）；Java 层 record/DTO 用 camelCase（`feedbackType`、`isQuery`），二者通过 `@JsonProperty("snake_case")` 显式桥接。

**Why**：Spec 契约字段与 LLM 输出均为 snake_case，而 Jackson 默认只做 camelCase 映射、不会把 snake_case 自动映射到 camelCase 组件，导致字段静默为 null（M3 已在 `is_query`、`feedback_type` 各踩坑一次）。

**How to apply**：凡新增 record/DTO，凡字段名含下划线的契约，一律加 `@JsonProperty` 显式映射；不加即视为漏映射风险。

## codegraph

目录已索引（`.codegraph/`）。修改 / 重构代码前，用 `codegraph_explore` 查影响范围（blast radius）与调用链，勿用 grep + read 重复劳动。
