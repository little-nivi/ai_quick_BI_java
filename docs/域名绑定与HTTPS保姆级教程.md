# 域名绑定与 HTTPS 保姆级教程（ICP 备案通过后）

> 适用场景：域名 ICP 备案已通过审核，要把域名绑定到阿里云 ECS 公网 IP，让 `http://你的域名` 替代 `http://123.57.53.23` 访问 NL2SQL 系统。
> 日期：2026-09-09
> 关联文档：
> - `docs/测试执行Runbook.md` — 测试流程（第 0 轮环境确认命令可复用）
> - `docs/LLM_API_Key替换手册.md` — 环境变量类操作先例
>
> **好消息**：前端 baseURL 是相对路径 `/api/v1`（走 Nginx 代理），后端代码和前端代码**都不需要任何修改**，全程只动 DNS 控制台和 Nginx 配置。

---

## 一、流程总览

```
① 添加 DNS 解析记录（控制台，5 分钟）
        ↓
② 等阿里云备案状态同步（被动等待，一般 2 小时内）
        ↓
③ 验证解析生效（本地，1 分钟）
        ↓
④ 配置 Nginx server_name（SSH，5 分钟）
        ↓
⑤ 验证 HTTP 访问（本地，2 分钟）
        ↓
⑥ （可选）升级 HTTPS：Let's Encrypt 免费证书（SSH，15 分钟）
        ↓
⑦ （可选）合规收尾：页脚挂备案号
```

### 准备信息

| 项目 | 值 |
|---|---|
| ECS 公网 IP | `123.57.53.23` |
| 你的域名 | 下文统一用 `yourdomain.com` 占位，**执行时全部替换成你的真实域名** |
| 阿里云账号 | 备案用的那个账号（域名和 ECS 需在同一阿里云账号下） |
| 前置条件 | 域名已实名认证；备案通过短信/邮件已收到 |

---

## 二、第 1 步：添加 DNS 解析记录（阿里云控制台）

1. 登录阿里云控制台 → 顶部搜索 **云解析 DNS** → 点击你的域名右侧 **解析设置**
2. 点击 **添加记录**，填两条：

| # | 记录类型 | 主机记录 | 记录值 | TTL | 说明 |
|---|---|---|---|---|---|
| 1 | A | `@` | `123.57.53.23` | 10 分钟 | yourdomain.com 本身 |
| 2 | A | `www` | `123.57.53.23` | 10 分钟 | www.yourdomain.com |

3. 保存后等 5-10 分钟生效（TTL 10 分钟 = 缓存 10 分钟）。

### 验证解析（本地 PowerShell）

```powershell
nslookup yourdomain.com 223.5.5.5     # 指定阿里公共 DNS，避开运营商缓存
nslookup www.yourdomain.com 223.5.5.5
```

通过标准：返回的 `Address` 是 `123.57.53.23`。

> 若返回旧地址或不存在 → 等 10 分钟再试；控制台检查记录是否保存成功。

---

## 三、第 2 步：等待备案状态同步（重要，不要跳过）

阿里云对**大陆地域 ECS** 的 80/443 端口做域名级备案校验。备案虽已通过，但阿里云系统把备案状态同步到你这台 ECS **需要一定时间（一般 2 小时内，最长约 24 小时）**。

- 这一步**不需要任何操作**，等即可。
- 如果第 1 步解析已生效，但访问域名返回 **403**，页面提示"未备案或备案信息未同步"→ 就是本步没完成，继续等。
- **同步完成前不要跳到第 6 步配 HTTPS**，Let's Encrypt 的 HTTP-01 验证也走 80 端口，会被 403 拦住导致签发失败。

---

## 四、第 3 步：配置 Nginx server_name（SSH 执行）

### 3.1 查看当前配置

```bash
grep -rn "server_name" /etc/nginx/
```

记下输出里出现 `server_name` 的文件路径（通常在 `/etc/nginx/nginx.conf` 或 `/etc/nginx/conf.d/xxx.conf`）。

### 3.2 备份配置文件

```bash
TS=$(date +%Y%m%d_%H%M%S)
cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.bak_${TS}
# 若 server_name 在 conf.d 下的其他文件，同样备份该文件
```

### 3.3 修改 server_name

编辑 3.1 找到的文件，把对应 `server` 块里的 `server_name` 改为：

```nginx
server_name yourdomain.com www.yourdomain.com;
```

说明：
- 原来写的是 `_` 或 `123.57.53.23` → 直接替换成上面这行（`_` 保留兜底也可以，多写几个值用空格分隔）；
- 如果当前配置里 `server_name` 有多个 server 块（如 80 跳转 443 的块），**每一块都改**；
- 80 端口的 `server` 块和 443 端口的 `server` 块都要含域名。

### 3.4 检查并重载

```bash
nginx -t                          # 必须显示 syntax is ok / test is successful
systemctl reload nginx
```

> `nginx -t` 报错 → 按报错行号改配置，**不要**强行 reload。

---

## 五、第 4 步：验证 HTTP 访问（本地 PowerShell）

```powershell
# ① 后端健康检查（走 Nginx 代理）
curl.exe -s -o NUL -w "health=%{http_code}`n" http://yourdomain.com/actuator/health

# ② 登录接口
curl.exe -s -X POST http://yourdomain.com/api/v1/auth/login -H "Content-Type: application/json" -d '"""{"username":"admin","password":"admin123"}"""'

# ③ 浏览器打开 http://yourdomain.com 确认前端页可登录
```

通过标准：
- ① `health=200`
- ② 返回含 `"code":2000` 和 token
- ③ 前端正常登录进主界面

失败处理：

| 现象 | 原因 | 处理 |
|---|---|---|
| 403 + "未备案" 提示 | 阿里云备案状态未同步完 | 回第 2 步继续等（最长 24h） |
| 403 + 提示接入信息不符 | 备案接入商不是阿里云 | 到备案控制台做「接入备案」变更接入商为阿里云 |
| 连接超时 | 安全组 80 未开放 | 见第 6 步开安全组 |
| 502 Bad Gateway | 后端 nl2sql-app 挂了 | SSH 执行 `systemctl status nl2sql-app`，按 Runbook 第 0 轮处理 |
| 解析不生效 | DNS 缓存 | 换 `nslookup yourdomain.com 223.5.5.5` 再验 |

---

## 六、（可选）第 5 步：升级 HTTPS — Let's Encrypt 免费证书

> 背景：此前方案是自签名证书（SAN IP，浏览器会有红色警告）。现在有域名了，升级 Let's Encrypt 正式证书（方案 12B），浏览器无警告，且自动续期。

### 6.1 确认安全组 80/443 已开放

ECS 控制台 → 实例 → 安全组 → 配置规则 → 入方向，确认存在 80 和 443 的放行规则（源 0.0.0.0/0）。没有就手工添加。

```bash
# SSH 验证端口监听
ss -tlnp | grep -E ':80|:443'
```

### 6.2 备份 Nginx 配置（certbot 会自动改配置）

```bash
TS=$(date +%Y%m%d_%H%M%S)
cp -r /etc/nginx /etc/nginx.bak_${TS}
```

### 6.3 安装 certbot

```bash
# Ubuntu/Debian
apt update && apt install -y certbot python3-certbot-nginx

# CentOS/Alibaba Cloud Linux
# yum install -y certbot python3-certbot-nginx
```

### 6.4 签发并自动配置证书

```bash
certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

交互提示：
- `Enter email address` → 填你的邮箱（到期提醒用）
- `Agree` → 输 Y
- `Redirect HTTP to HTTPS?` → 选 **2**（强制 HTTPS，推荐）

certbot 会自动：签发证书 → 改写 Nginx 的 `ssl_certificate` 路径 → reload。原来自签证书的配置**不需要手动清理**，certbot 直接替换。

### 6.5 验证自动续期

```bash
certbot renew --dry-run     # 必须显示 Congratulations / simulating renewal 成功
systemctl list-timers | grep certbot   # 确认续期定时器存在
```

### 6.6 验证 HTTPS（本地 PowerShell）

```powershell
curl.exe -s -o NUL -w "https_health=%{http_code}`n" https://yourdomain.com/actuator/health
```

通过标准：`https_health=200`，浏览器访问 `https://yourdomain.com` 地址栏无警告（小锁图标）。

---

## 七、（可选）第 6 步：合规收尾

1. **前端页脚挂备案号**（管局会抽查，建议加上）：在前端页脚组件加一行，格式：

   ```
   <a href="https://beian.miit.gov.cn" target="_blank">你的备案号，如 粤ICP备2026XXXXXX号</a>
   ```

2. **统一访问地址**（可选）：Runbook 里冒烟测试默认走公网 IP `http://123.57.53.23`，可把常用命令换成域名；`scripts/eval_accuracy.py` 的默认 `--base` 无需改动（传参即可覆盖）。

---

## 八、完成标准 Checklist

- [ ] `nslookup yourdomain.com` → `123.57.53.23`
- [ ] `http://yourdomain.com/actuator/health` → 200
- [ ] 浏览器 `http://yourdomain.com` 可登录前端
- [ ] （做了 HTTPS）`https://yourdomain.com/actuator/health` → 200，浏览器无证书警告
- [ ] （做了 HTTPS）`certbot renew --dry-run` 成功
- [ ] （合规）页脚展示备案号

全部打勾后，本项目对外地址正式从 IP 切换为域名，此前的部署规范不变（8080 仍不对公网暴露，/api/ 与 /actuator/health 仍走 Nginx 代理）。

---

## 九、回滚预案

```bash
# Nginx 配置出问题时，恢复备份并重载
cp /etc/nginx/nginx.conf.bak_时间戳 /etc/nginx/nginx.conf
nginx -t && systemctl reload nginx

# HTTPS 证书出问题时，临时回退自签名（恢复第 6.2 步的整目录备份）
systemctl stop nginx
rm -rf /etc/nginx && mv /etc/nginx.bak_时间戳 /etc/nginx
systemctl start nginx

# 彻底回到纯 IP 访问：控制台删除两条 A 记录即可，其余不用动
```

---

## 十、常见问题 FAQ

| 问题 | 答案 |
|---|---|
| 备案通过了但访问域名 403？ | 阿里云备案状态同步未完成，等 2 小时（最长 24h）再试 |
| 域名解析一定要用阿里云 DNS 吗？ | 不强制，但备案在阿里云、解析也用阿里云最省事；第三方 DNS 同样能加 A 记录 |
| 不加 www 记录行吗？ | 行，`@` 一条就够；加 www 是方便习惯输 www 的用户 |
| Let's Encrypt 证书多久到期？ | 90 天，certbot 定时器会自动续，无需人工干预 |
| 还需要把 8080 暴露出去吗？ | **不要**。安全规范不变：8080 仅本机访问，外部一律走 Nginx 代理 |
| 绑域名后测试脚本要改吗？ | 不强制。`eval_accuracy.py` 用 `--base http://yourdomain.com` 传参即可切换 |
