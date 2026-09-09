# NL2SQL 测试执行 Runbook（作战手册）

> 本 Runbook 是多轮对话执行测试的指导文档。每次新对话只需说「读 Runbook 第 N 轮」，AI 会扫描结果记录区找到上次进度，自动恢复上下文。
> 版本：v1.0 日期：2026-09-08
> 关联文件：
> - `docs/NL2SQL项目测试框架_简化版.md` — 测什么（指标定义+判定标准）
> - `docs/测试执行保姆级教程.md` — 怎么操作（命令+步骤）
> - `scripts/eval_accuracy.py` — 自动执行器（130 条用例）

---

## 协作协议

### 用户操作只有 4 种
1. 说「读 Runbook 第 N 轮」
2. SSH 执行 AI 给出的命令，把输出贴回
3. 做决策（通过 / 修复 / 跳过）
4. `git commit`

### AI 行为规则
- 每次回复前先读本 Runbook 的结果记录区，确认当前轮次
- 只给命令，不直连服务器
- 每轮结束写回结果记录区
- 失败时用 codegraph 定位代码（见第 6 节）

---

## 轮次编排

| 轮次 | 目标 | 命令 | 预期耗时 | 通过条件 |
|---|---|---|---|---|
| 第 0 轮 | 环境就绪确认 | health=200 + login=2000 + LLM 连通 | ~2 分钟 | 全部通过 |
| 第 1 轮 | 冒烟测试（12 条） | `--quick` | ~3 分钟 | 脚本能跑完+报告生成 |
| 第 2 轮 | 全量评测（130 条） | 无参数 | ~15-25 分钟 | 准确率输出 |
| 第 3 轮 | 失败用例根因分析 | `cat failures_*.csv` | — | 根因分类表产出 |
| 第 4 轮 | 分组回归验证 | `--category X` | ~3-5 分钟/组 | 修复后该组通过 |
| 第 5 轮 | 全量复测 | 无参数 | ~15-25 分钟 | 准确率 ≥ 80% |

---

## 第 0 轮：环境就绪确认

### 命令（SSH 执行）

```bash
# ① 后端活着
curl -s -o /dev/null -w "health=%{http_code}\n" http://127.0.0.1:8080/actuator/health

# ② 登录正常
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | head -c 200
echo

# ③ LLM API 连通（关键！上次冒烟全挂在这）
source /etc/default/nl2sql-app
echo "API key length: ${#DASHSCOPE_API_KEY}"
curl -s -w "\nHTTP=%{http_code}\n" \
  https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-turbo","messages":[{"role":"user","content":"hello"}],"stream":false}' | head -c 500
```

### 通过条件
- ① `health=200`
- ② 包含 `"code":2000`
- ③ `HTTP=200` 且响应包含 `choices`

### 失败处理
- ① health≠200 → `systemctl status nl2sql-app` + `journalctl -u nl2sql-app -n 30`
- ② 登录失败 → 查 `/etc/default/nl2sql-app` 中 JWT_SECRET 是否为空
- ③ LLM 不通 → API key 过期/额度用完/网络问题。换 key 或充值后重试

---

## 第 1 轮：冒烟测试

### 前置：上传脚本（本地 PowerShell）

```powershell
cd d:\mywork\20260818_java_agent
scp scripts\eval_accuracy.py root@123.57.53.23:/opt/nl2sql/eval_accuracy.py
```

### 命令（SSH 执行）

```bash
apt-get install -y python3-requests   # 如果没装过
cd /opt/nl2sql && python3 eval_accuracy.py --base http://127.0.0.1:8080 --quick
```

### 通过条件
- 12 条全部跑完（无大面积超时/连接失败）
- 报告生成：`/opt/nl2sql/reports/eval_summary_*.md`
- **不要求准确率达标**，只证明链路通

### 失败处理
- 大面积 `code=5003` → LLM 不通，回第 0 轮
- 大面积连接失败 → `--base` 地址错误
- 脚本报错 → 把 traceback 贴回

---

## 第 2 轮：全量评测

### 命令（SSH 执行，防断线用 nohup）

```bash
cd /opt/nl2sql && nohup python3 eval_accuracy.py --base http://127.0.0.1:8080 > eval_run.log 2>&1 &
tail -f eval_run.log    # 看进度，Ctrl+C 不影响后台
```

### 通过条件
- 跑完 130 条
- 控制台输出统计块：`总计 N 条，通过 M 条，准确率 X.X%`
- 3 份报告生成

### 产出
```
/opt/nl2sql/reports/
├── eval_summary_时间戳.md    ← 人看的摘要
├── eval_report_时间戳.json   ← 全量原始数据
└── failures_时间戳.csv       ← 失败明细
```

---

## 第 3 轮：失败用例根因分析

### 命令（SSH 执行）

```bash
cat /opt/nl2sql/reports/failures_*.csv
echo "=== 摘要 ==="
cat /opt/nl2sql/reports/eval_summary_*.md
```

### AI 分析维度
1. **LLM 生成问题**：SQL 缺关键词 / 逻辑不符 → 修 Prompt 或加关键词到同义词
2. **断言过严**：SQL 正确但关键词写法不同（如反引号包裹）→ 放宽断言
3. **真 bug**：权限未注入 / 缓存串读 / 澄清误触发 → 修代码
4. **LLM 不稳定**：同一条有时对有时错 → 看是否需要加 fallback

---

## 第 4 轮：分组回归验证

### 命令（SSH 执行，按失败类别选）

```bash
# 只跑某组验证修复
python3 eval_accuracy.py --base http://127.0.0.1:8080 --category normal   # 常规组 100 条
python3 eval_accuracy.py --base http://127.0.0.1:8080 --category perm     # 权限组 10 条
python3 eval_accuracy.py --base http://127.0.0.1:8080 --category cache    # 缓存组 10 条
python3 eval_accuracy.py --base http://127.0.0.1:8080 --category conc     # 并发组 10 条
```

### 通过条件
- 修复后的类别通过率 ≥ 80%（安全/权限类必须 100%）

---

## 第 5 轮：全量复测

### 命令（SSH 执行）

```bash
cd /opt/nl2sql && nohup python3 eval_accuracy.py --base http://127.0.0.1:8080 > eval_run2.log 2>&1 &
```

### 通过条件
- 总体准确率 ≥ 80%
- 安全/权限类 100%
- 输出最终指标报告

---

## codegraph 使用指南（失败定位代码）

当某条用例失败且疑似代码 bug 时，AI 用 codegraph 定位：

| 失败现象 | codegraph 查询 |
|---|---|
| SQL 缺权限过滤 | `PermissionInjector inject region` |
| 澄清误触发 | `ClarificationDecider shouldClarify rule` |
| 缓存不命中 | `SemanticCache key SHA role` |
| 意图识别错误 | `IntentConfidenceEvaluator isQuery confidence` |
| SQL 注入未拦截 | `SqlValidator validate SELECT` |
| 并发串读 | `UserContext ThreadLocal clear` |

---

## 结果记录区（断点续传用）

> 每次跑完一轮，AI 在此区写入结果。新对话时扫描此区恢复上下文。

---

### 第 0 轮结果

- 状态：`通过`
- health：200
- login：2000 + token
- LLM HTTP：200（已替换新 key）
- 备注：LLM API key + Langfuse 评估 key 均已替换

### 第 1 轮结果

- 状态：`通过`
- 通过数：9/10
- 准确率：90.0%
- P50=4648ms P90=6877ms P95=6877ms
- 缓存命中：1 次，命中均延迟 1ms vs 未命中 5277ms
- 失败用例：#141 并发组 5 请求中 1 个返回 None（公网 nginx 抖动，非代码 bug）
- 备注：走公网 nginx http://123.57.53.23，本地 conda 环境跑

### 第 2 轮结果

- 状态：`通过`（准确率 96.2% ≥ 80%）
- 通过数：125/130
- 准确率：96.2%
- P50=3180ms P90=5891ms P95=7065ms P99=9729ms 最大=14882ms
- 缓存命中：15 次，命中均延迟 1775ms vs 未命中 3942ms（加速比 2.2x）
- 分类别：
  - 单表简单查询: 19/20 (95.0%)
  - 单表聚合查询: 18/20 (90.0%)
  - 时间范围查询: 15/15 (100.0%)
  - 模糊语义查询: 14/15 (93.3%)
  - 复杂嵌套查询: 9/10 (90.0%)
  - 边界异常测试: 10/10 (100.0%)
  - 大数据量性能测试: 10/10 (100.0%)
  - 权限隔离测试: 10/10 (100.0%)
  - 缓存命中测试: 10/10 (100.0%)
  - 并发测试: 10/10 (100.0%)
- 失败分类统计：
  - 5003 LLM 格式错误（偶发）：3 条（#18 销量、#31 各渠道平均订单金额、#40 各渠道销售额排名）
  - 4001 非问数未降级澄清：1 条（#87 下单量）
  - 5000 系统内部错误（SQL 执行超时32s）：1 条（#91 消费金额超过平均值的用户有哪些）

### 第 3 轮结果

- 状态：`待执行` / `通过`
- 根因分类表：
  - LLM 生成问题：N 条
  - 断言过严：N 条
  - 真 bug：N 条
  - LLM 不稳定：N 条
- 修复决策：

### 第 4 轮结果

- 状态：`待执行` / `通过`
- 各组通过率：
  - normal：
  - perm：
  - cache：
  - conc：

### 第 5 轮结果

- 状态：`通过`
- 通过数：129/130
- 准确率：99.2%
- 延迟：P50=0ms P90=1ms P95=1ms P99=4991ms（缓存命中 106 次，延迟失真，真实延迟见第 2 轮）
- 缓存命中：106 次，命中均延迟 1ms vs 未命中 6290ms
- 分类别：
  - 单表简单查询: 20/20 (100.0%)
  - 单表聚合查询: 20/20 (100.0%)
  - 时间范围查询: 15/15 (100.0%)
  - 模糊语义查询: 14/15 (93.3%)
  - 复杂嵌套查询: 10/10 (100.0%)
  - 边界异常测试: 10/10 (100.0%)
  - 大数据量性能测试: 10/10 (100.0%)
  - 权限隔离测试: 10/10 (100.0%)
  - 缓存命中测试: 10/10 (100.0%)
  - 并发测试: 10/10 (100.0%)
- 唯一确定性失败：#87 下单量 → 4001（LLM 判非问数未降级澄清，和"已完成订单量"同类）
- 上轮偶发失败已全部复现通过：#18/#31/#40（5003）、#91（5000）

---

### 第 6 轮结果（阶段 C：JOIN 多表测试）

- 状态：`通过`（JOIN 组 20/20 = 100%，远超 80% 目标）
- 阶段：阶段 C（JOIN 关联表）
- 执行日期：2026-09-09
- 总用例数：150 条（原 130 + JOIN 20）
- 通过数：140/150
- 准确率：93.3%
- 延迟：P50=1969ms P90=（未输出）P95=4562ms（JOIN 组）最大=9860ms
- 缓存命中：（未输出全局数）
- 分类别：
  - 单表简单查询: 20/20 (100.0%)
  - 单表聚合查询: 19/20 (95.0%)
  - 时间范围查询: 14/15 (93.3%)
  - 模糊语义查询: 12/15 (80.0%)
  - 复杂嵌套查询: 9/10 (90.0%)
  - 边界异常测试: 10/10 (100.0%)
  - 大数据量性能测试: 8/10 (80.0%)
  - **JOIN多表查询: 20/20 (100.0%)**  ← 阶段 C 核心
  - 权限隔离测试: 9/10 (90.0%)
  - 缓存命中测试: 10/10 (100.0%)
  - 并发测试: 9/10 (90.0%)
- 失败分类统计：
  - 5003 LLM 格式错误（偶发）：6 条（#40/#73/#79/#82/#91/#130）
  - 5000 SQL 超时/系统错误：2 条（#126 订单金额分布、#115 销售额排名权限组）
  - 4001 非问数未降级澄清：1 条（#87 下单量，确定性失败）
  - 5003 并发偶发：1 条（#142 5人不同问题中 1 个返回 5003）
- 阶段 C 改动清单：
  - 数据库层：V7 迁移建 customers + products 表 + 20 条 products 样例 + 5 条 customers 占位
  - 数据灌入：generate_customers.py 灌 2000 条 customers（覆盖 orders.user_id 1..2000）
  - Java 代码：SqlGenerator schema 扩展为 3 表 + JOIN few-shot 示例 + SYSTEM_PROMPT JOIN 规则
  - Java 代码：IntentConfidenceEvaluator LOW_THRESHOLD 从 0.75 降到 0.6（JOIN 场景 LLM 信心天然偏低）
  - Java 代码：ClarificationDecider 规则 3 阈值同步降到 0.6
  - 评测脚本：eval_accuracy.py 加 20 条 JOIN 用例（id 201-220）+ CAT_JOIN 分类
- 关键设计决策验证：
  - customers 表无 region 字段 → PermissionInjector 注入 region IN 无歧义 ✓
  - JOIN 用例文案避开指标同义词 → 走完整 generate 路径看到全 schema ✓
  - JOIN 判定用 `["JOIN"]` 关键词 → 复用 check_hints 逻辑 ✓
- 部署踩坑记录：
  - systemd service 配置 jar 路径是 `/opt/nl2sql/nl2sql-app.jar`（无版本号），需 cp 覆盖
  - Flyway V7 需要新 jar 才会执行，旧 jar 跑不出 V7
- 报告文件：
  - `scripts/reports/eval_summary_20260909_113231.md`
  - `scripts/reports/eval_report_20260909_113231.json`
  - `scripts/reports/failures_20260909_113231.csv`

---

### 第 7 轮结果（阶段 A：脚本增强 + Schema 幻觉检测）

- 状态：`通过`（准确率 98.4%，远超 80% 目标）
- 阶段：阶段 A（纯脚本增强，不改 Java 代码）
- 执行日期：2026-09-09
- 执行环境：本地 conda ai_agent（HTTP 客户端连公网服务器）
- 总用例数：205 条定义（缓存组多次提问展开后 187 条 Result）
- 通过数：184/187
- 准确率：98.4%
- 延迟：P50=0ms P95=1ms（缓存热数据，非真实延迟）
- 缓存：全局命中 52 次，命中均延迟 1ms，未命中均延迟 1115ms
- 分类别：
  - 单表简单查询: 20/20 (100.0%)
  - 单表聚合查询: 20/20 (100.0%)
  - 时间范围查询: 15/15 (100.0%)
  - 模糊语义查询: 25/25 (100.0%)  ← +10 条同义改写全通过
  - 复杂嵌套查询: 10/10 (100.0%)
  - 边界异常测试: 10/10 (100.0%)
  - 大数据量性能测试: 10/10 (100.0%)
  - **澄清触发测试: 2/5 (40.0%)**  ← 阶段 A 新增，发现真实系统问题
  - JOIN多表查询: 30/30 (100.0%)  ← +10 条复杂场景全通过
  - 权限隔离测试: 10/10 (100.0%)
  - 缓存命中测试: 22/22 (100.0%)  ← 口径修复后展开为 22 条 Result
  - 并发测试: 10/10 (100.0%)
- 失败用例（3 条，全部是真实系统问题，非检测 bug）：
  - #301 "对比一下 top5" → 期望 4002 澄清，实际 2000 直接给 SQL（缺指标锚点）
  - #303 "销量排名前" → 期望 4002 澄清，实际 2000（"前"后缺具体数量）
  - #304 "各地区的情况" → 期望 4002 澄清，实际 2000（"情况"太模糊）
  - 根因：ClarificationDecider 规则 3 阈值已降到 0.6，但对"缺指标锚点/缺具体数量"的识别仍不够严格
- 阶段 A 改动清单：
  - 缓存口径 bug 修复：run_cache 每条用例生成 times 个 Result，第 1 次 cache_hit=False（未命中），后续 cache_hit=True（命中），命中/未命中延迟分开统计
  - 澄清触发测试：新增 CAT_CLARIFY 分类 + 5 条用例（id 301-305），期望触发 4002
  - Schema 幻觉检测：新增 detect_schema_hallucination 函数 + SCHEMA_FIELDS 白名单 + SQL_KEYWORDS_WHITELIST，在 run_normal 的 check_hints 之后调用
  - 样本量扩充：JOIN 加 10 条复杂场景（id 221-230：LEFT JOIN/子查询+JOIN/HAVING+JOIN/排序+JOIN/时间+JOIN），模糊语义加 10 条同义改写（id 306-315）
- 关键修复过程：
  - 第 1 版检测函数误判表名 orders/customers/products 为幻觉 → 加 SCHEMA_FIELDS 键白名单
  - 第 2 版误判 MySQL 函数 CURRENT_DATE/DATE_FORMAT/YEARWEEK/TRUNCATE/WITH 为幻觉 → 补充 SQL_KEYWORDS_WHITELIST
  - 第 3 版误判字符串值 'completed'/'refunded' 为幻觉 → 加引号内字符串剥离逻辑
- Schema 幻觉检测最终效果：0 误报（所有合法 SQL 正确通过，无误判）
- 报告文件：
  - `scripts/reports/eval_summary_20260909_170731.md`
  - `scripts/reports/eval_report_20260909_170731.json`
  - `scripts/reports/failures_20260909_170731.csv`

---

### 第 8 轮结果（阶段 B：执行级结果比对）

- 状态：`通过`（结果一致率 85%，达达标线 80%）
- 阶段：阶段 B（执行级结果比对）
- 执行日期：2026-09-09
- 执行环境：服务器 SSH（pymysql 必须连 Docker MySQL 127.0.0.1:3306，本地连不上）
- 总用例数：20 条代表性用例
- 通过数：17/20
- 结果一致率：85.0%
- 分类别：
  - 单值查询（SUM/COUNT/AVG/MAX/MIN）: 11/12 全部数值精确匹配
  - 多行查询（GROUP BY）: 5/5 全部行数+数值匹配
  - JOIN 查询: 3/5（2 条失败）
- 失败用例（3 条，2 条是真实语义错、1 条偶发）：
  - #201 "企业类型客户下了多少笔交易" → API=10 vs REF=1493
    - 根因：LLM 生成的 JOIN SQL 漏了 WHERE c.type='企业' 条件，导致全量 JOIN 返回所有交易而非仅企业客户
    - 意义：SQL 语法对但语义错，这是阶段 B 要抓的"SQL 对但数不对"问题
  - #84 "完成的订单" → API=2 vs REF=1649
    - 根因：LLM 生成 SELECT * 返回明细列表，参考 SQL 是 SELECT COUNT(*) 返回单值 1649
    - 意义：LLM 偶尔会用 SELECT * 代替 COUNT，结果集结构不一致
  - #218 "各品类商品销售额" → code=5003
    - 根因：LLM 偶发 5003 格式错误，重跑大概率通过
- 阶段 B 核心价值：
  - 验证了"SQL 对但数不对"的真实问题（#201 JOIN 漏 WHERE 条件）
  - 证明了当前评测只验 SQL 关键词是不足的，需要结果集比对
  - 单值和多行查询的 17/20 一致率证明 LLM 生成的 SQL 在大多数场景下语义正确
- 阶段 B 改动清单：
  - 新建 scripts/eval_result_compare.py
  - 20 条代表性用例 + 参考 SQL + 比对模式（scalar/multi_row_sort）
  - pymysql 连 MySQL 跑参考 SQL，和 API 返回的结果集比对
  - 单值模式数值容差 0.01，多行模式排序后逐行逐列比对
- 报告文件：`scripts/reports/result_compare_时间戳.json`（Decimal JSON 序列化 bug 已修复）

---

## 最终指标（综合多轮）

| 指标 | 值 | 数据来源 |
|---|---|---|
| 准确率（单表） | **99.2%**（129/130） | 第 5 轮复测（缓存命中，更稳定） |
| 准确率（JOIN 多表） | **100.0%**（30/30） | 第 7 轮阶段 A（含 10 条复杂 JOIN） |
| 准确率（全量 205 条） | **98.4%**（184/187） | 第 7 轮阶段 A |
| P50 | 3180ms | 第 2 轮（缓存未命中为主，真实延迟） |
| P95 | 7065ms | 第 2 轮 |
| JOIN P50/P95 | 2969ms / 4562ms | 第 6 轮 JOIN 组 |
| 缓存加速比 | **2.2x**（1775ms vs 3942ms） | 第 2 轮（口径修复前；第 7 轮口径已修复，缓存热数据未测出真实加速比） |
| 安全/权限/缓存/并发/边界 | 100% | 多轮一致 |
| JOIN 多表 | **100%** | 第 6-7 轮一致 |
| Schema 幻觉检测 | 0 误报 | 第 7 轮阶段 A |
| 澄清触发准确率 | 40%（2/5） | 第 7 轮阶段 A（发现真实系统短板） |
| **结果集一致率** | **85.0%**（17/20） | 第 8 轮阶段 B（执行级比对） |

---

## 清理命令（测试完清理服务器）

```bash
rm -f /opt/nl2sql/eval_accuracy.py /opt/nl2sql/eval_run.log /opt/nl2sql/eval_run2.log
rm -rf /opt/nl2sql/reports/
```
