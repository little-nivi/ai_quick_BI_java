# LLM API Key 替换手册

> 适用场景：DashScope API key 过期 / 额度用完 → 替换后让后端 + Langfuse 评估恢复正常。
> 本项目代码无硬编码 key，全部从环境变量读取，替换只需改配置文件 + 重启，不改代码。

---

## 一、Key 在哪里被读取

| Key | 代码位置 | 环境变量 | 用途 |
|---|---|---|---|
| DashScope API Key | `application-dev.yml:14` | `DASHSCOPE_API_KEY` | 后端调用 qwen-turbo 生成 SQL |
| Langfuse Public Key | `application-dev.yml:41` | `LANGFUSE_PUBLIC_KEY` | OTel 链路上报鉴权 |
| Langfuse Secret Key | `application-dev.yml:42` | `LANGFUSE_SECRET_KEY` | OTel 链路上报鉴权 |

环境变量统一存放在服务器 `/etc/default/nl2sql-app`。

---

## 二、替换后端 LLM API Key（DASHSCOPE_API_KEY）

### 步骤 1：编辑环境变量文件

```bash
nano /etc/default/nl2sql-app
```

找到这一行：

```
DASHSCOPE_API_KEY=sk-旧的key
```

替换为新 key：

```
DASHSCOPE_API_KEY=sk-新的key
```

保存：`Ctrl+O` → 回车 → `Ctrl+X` 退出。

### 步骤 2：重启后端服务

```bash
systemctl restart nl2sql-app
sleep 20
```

### 步骤 3：验证新 key 生效

```bash
source /etc/default/nl2sql-app
echo "API key length: ${#DASHSCOPE_API_KEY}"

curl -s -w "\nHTTP=%{http_code}\n" \
  https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-turbo","messages":[{"role":"user","content":"hello"}],"stream":false}' | head -c 300
```

**预期输出**：`HTTP=200` 且响应 JSON 包含 `choices` 字段。

### 步骤 4：验证后端问数恢复

```bash
# 登录拿 token
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token','NO_TOKEN'))")
echo "token len: ${#TOKEN}"

# 问一条简单问题
curl -s -X POST http://127.0.0.1:8080/api/v1/query \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"总销售额是多少"}' | head -c 400
echo
```

**预期**：返回 `code=2000` 且 `data.sql` 包含 `SUM`。

---

## 三、替换 Langfuse 评估用的 LLM API Key

Langfuse 评估（Scores 自动评分）用的 LLM key 配置在 **Langfuse Cloud 控制台**，不在我们服务器上。

### 步骤 1：登录 Langfuse Cloud

打开 [https://jp.cloud.langfuse.com](https://jp.cloud.langfuse.com)，用你的账号登录。

### 步骤 2：进入 Model Providers 配置

路径：**Settings → Model Providers**（或 LLM API Keys）。

### 步骤 3：替换 API Key

1. 找到过期的 DashScope/qwen API key
2. 删除旧 key
3. 添加新的 API key（选择 DashScope 或 OpenAI-compatible，填入新 key）
4. 确认评估器（Evaluator）里配置的 model 名称与新 key 支持的 model 一致

### 步骤 4：验证评估器可用

在 Langfuse 的 **Evaluations** 页面，手动触发一次评估，确认能正常出分。

---

## 四、常见问题排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| curl 返回 `HTTP=401` | key 错误或格式不对 | 检查 key 前后有无空格/换行 |
| curl 返回 `HTTP=403` 或 `Insufficient quota` | key 有效但额度用完 | 充值或换新 key |
| curl 返回 `Connection timed out` | 服务器网络不通 | `curl -I https://dashscope.aliyuncs.com` 测试连通性 |
| 重启后后端仍返回 5003 | 环境变量没生效 | `systemctl show nl2sql-app --property=Environment` 确认 key 已加载 |
| Langfuse 评估不出分 | 评估器配置的 model 名不匹配新 key | 在 Langfuse 控制台检查 Evaluator 的 model 配置 |

---

## 五、Key 安全提醒

1. **不要把 key 提交到 Git**：`/etc/default/nl2sql-app` 在服务器上，不在代码仓库里，天然隔离
2. **不要在日志里打印 key**：代码里用 `${DASHSCOPE_API_KEY:}` 占位符，日志不会输出明文
3. **定期轮换**：建议每 90 天换一次 key，降低泄漏风险
4. **最小权限**：DashScope key 只开 qwen-turbo 等必要模型的权限，不要开全量模型
