# ADR-0012 LangFuse 观测决策确认

> 状态：已决（D-34~D-37 全部确认）
> 日期：2026-08-23
> 确认人：用户（"全部照单，接入 token 用量"）
> 上游依据：章程 M6 里程碑（LangFuse 接入 + 成本/延迟监控）

---

## D-34 观测粒度

- **决策**：每次 `/api/v1/query` 生成一个 **trace**，内含一个 **generation**（LLM 调用）。
  - trace：question、generated_sql、latency_ms、cache_hit、confidence_level、matched_metric、role；
  - generation：输入 prompt、输出 sql、model、token 用量、耗时。

## D-35 实现方式（REST 直连）

- **决策**：REST 直连 LangFuse ingestion API（`POST {base}/api/public/ingestion`，Basic auth），不引 SDK。
- **理由**：Maven Central 无 langfuse-java SDK；与 ADR D-09（DashScope REST 直连）路线一致。
- **已连通验证**：Python 直连 `jp.cloud.langfuse.com` 返回 207，trace 写入成功。

## D-36 异步发送 + 静默降级

- **决策**：观测发送**异步**（单线程 executor），失败仅打 WARN 日志，绝不阻塞/影响 query 主链路。
- **降级**：public/secret key 未配置时，observability 为 no-op；发送异常静默忽略。

## D-37 接入 token 用量

- **决策**：接入 qwen 返回的 `usage`（prompt_tokens/completion_tokens/total_tokens）。
- **实现**：`LlmClient.generate` 返回 `LlmResult(content, usage)`；`SqlGenerator` 返回 `SqlGeneration(response, usage)`；QueryService 透传给观测层。
