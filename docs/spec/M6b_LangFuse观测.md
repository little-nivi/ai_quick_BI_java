# M6b LangFuse 观测 Spec

> 版本：v0.1　日期：2026-08-23　状态：待签字
> 遵守标准：《SPEC_CODING_STANDARD_v2.md》v2.2　全局约定见 `00_总览_非目标_变更记录.md`
> 上游依据：章程 M6 里程碑（LangFuse 接入 + 成本/延迟监控）、《ADR-0012》

---

## 0. 非目标（不做）清单

- 不做 LangFuse prompt 管理 / 评估 / 数据集（仅做 trace 观测）
- 不做成本告警推送（仅记录，告警策略留后续）
- 不做 LangFuse 本地自建（用 Cloud，jp.cloud.langfuse.com）
- 不做用户行为分析（仅 LLM 调用观测）

---

## 1. 业务目标 & 非功能需求（§2.1）

### 系统能力

每次问数请求生成 LangFuse trace（含 LLM generation），记录问题、SQL、耗时、token、置信度、缓存命中，实现全链路可观测。

### 非功能需求（量化）

| 编号 | NFR | 值 | 测量方式 |
|---|---|---|---|
| NFR-20 | 观测发送不阻塞主链路 | 异步，主链路延迟影响 ≈ 0 | 观测耗时不计入 latency_ms |
| NFR-21 | 观测失败不影响功能 | 100% 静默降级 | 断网/无 key 时 query 仍正常返回 |

### 安全红线

| 编号 | 红线 | 拦截触发条件 | 拦截动作 |
|---|---|---|---|
| SR-11 | 观测 key 不落盘 | 代码/配置文件含明文 key | 禁止；仅环境变量注入 |

---

## 2. 系统架构 & 模块划分（§2.2）

### 模块

| 模块 | 职责 | 依赖 |
|---|---|---|
| LangfuseClient | REST 直连 ingestion API，Basic auth | 外部 LangFuse |
| LangfuseObservability | 组装 trace+generation，异步发送，降级 | LangfuseClient |
| LlmClient | 返回 LlmResult（含 token usage） | 无 |

依赖方向：`QueryService → LangfuseObservability → LangfuseClient → LangFuse 云`。

---

## 3. 数据模型（§2.3）

无新增数据库表（LangFuse 云侧存储）。新增 Java record：`LlmUsage`、`LlmResult`、`SqlGeneration`。

---

## 4. 接口契约（§2.4）

无新增对外 HTTP 接口（LangFuse 为外部观测，不影响业务 API）。

---

## 5. 核心业务逻辑（§2.5）

### REQ-531 LangFuse trace 记录

- 每次 `/api/v1/query` 结束时（成功或失败），组装并异步发送一个 trace：
  - `name` = "nl2sql-query"；
  - `input` = question；
  - `output` = 生成的 SQL；
  - `metadata` = { latency_ms, cache_hit, confidence_level, matched_metric, role }。

### REQ-532 token 用量接入

- `LlmClient.generate` 解析 qwen 响应 `usage`，返回 `LlmResult(content, usage)`；
- `SqlGenerator` 透传 usage 至 `SqlGeneration(response, usage)`。

### REQ-533 异步发送 + 降级

- `LangfuseObservability.record(...)` 用单线程 executor 异步提交发送任务；
- key 未配置 → no-op；发送异常 → WARN 日志，不抛出。

---

## 6. 边界 Case、兜底策略、验收标准（§2.6）

### 验收 Case

```
TC-531-01（验证 REQ-531 trace 发送）
Given: LangFuse key 已配置
When:  一次 query 完成
Then:  LangFuse 后台收到一条 trace（含 question/sql/耗时）

TC-532-01（验证 REQ-532 token 用量）
Given: qwen 返回 usage 含 prompt_tokens/completion_tokens
When:  LlmClient.generate 执行
Then:  LlmResult.usage 非空且值正确

TC-533-01（验证 REQ-533 降级）
Given: LangFuse key 未配置
When:  一次 query 完成
Then:  query 正常返回，无异常抛出
```

### 验收成功判定

- 3 条 TC 全部通过；
- LangFuse 后台可见 trace + generation（含 token 用量）；
- 主链路延迟不受观测影响。

---

## 7. 变更记录

| 日期 | 变更人 | 变更 REQ | 变更说明 | 影响范围 |
|---|---|---|---|---|
| 2026-08-23 | 用户 | REQ-531~533 | LangFuse 观测接入 + token 用量 | M6 |
| 2026-08-23 | 用户 | REQ-531~533 | 实现完成：43 测试全绿，端到端验证通过（trace+generation 写入 LangFuse v4，含 token 用量） | M6 |

> 签字人：用户（"全部照单"）　日期：2026-08-23
