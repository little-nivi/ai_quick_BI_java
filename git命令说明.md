# Git 命令说明（NL2SQL 项目）

> 本文件是项目 Git 操作手册。**约定：AI 不执行任何 git 命令，所有 git 操作由你手动执行**（见 `CLAUDE.md`「Git 操作约定」）。
> 命令均为 Git Bash（MINGW64）下执行。

---

## 1. 日常提交流程

```bash
cd /d/mywork/20260818_java_agent

# 1. 查看工作区状态（提交前必看）
git status

# 2. 查看本次改动内容（可选，确认无误）
git diff --cached --stat      # 已暂存改动的摘要
git diff --cached             # 已暂存改动的完整 diff

# 3. 暂存改动
git add -A                    # 暂存所有改动（新增+修改+删除）
# 或精确指定文件
git add src/main/java/.../Xxx.java

# 4. 提交
git commit -m "类型: 简述 // REQ-xxx"

# 5. 推送到远程
git push
```

**commit message 规范**：`<类型>: <简述> // REQ-<模块号>-<流水>`
- 类型：`feat`（新功能）、`fix`（修复）、`test`（测试）、`docs`（文档）、`refactor`（重构）
- 示例：`feat(m3): 意图识别+置信度+反问澄清 // REQ-511~515`

---

## 2. 查看历史与状态

```bash
git log --oneline            # 简洁提交历史
git log --oneline -5         # 最近 5 条
git log --oneline --all      # 含所有分支/游离提交
git show <commit-hash>       # 查看某次提交的完整内容
git show --stat <commit-hash> # 查看某次提交改了哪些文件
git status                   # 工作区状态
git diff                     # 工作区未暂存的改动
git diff --cached            # 已暂存的改动
```

---

## 3. 版本回退与恢复（重要，本次踩坑经验）

### 3.1 撤销工作区改动（未 add）

```bash
git checkout -- <文件>       # 丢弃单个文件的工作区改动
git checkout -- .            # 丢弃所有未暂存的改动（危险，慎用）
```

### 3.2 撤销暂存（已 add，未 commit）

```bash
git restore --staged <文件>  # 取消暂存，改动回到工作区
git restore --staged .       # 取消全部暂存
```

### 3.3 撤销提交

```bash
git reset --soft HEAD~1      # 撤销最近 1 次提交，改动保留在暂存区
git reset --mixed HEAD~1     # 撤销提交，改动回到工作区（默认）
git reset --hard HEAD~1      # 撤销提交，改动完全丢弃（危险！）
```

### 3.4 从历史恢复某个文件（本次用到的关键命令）

当某个文件被错误回退/丢失时，从指定提交恢复它：

```bash
git checkout <commit-hash> -- <文件路径>
# 示例：从提交 b9c6d4b 恢复 pom.xml
git checkout b9c6d4b -- pom.xml
```

恢复后文件会进入**暂存区**（staged），记得 `git commit`。

### 3.5 找回"丢失"的提交

```bash
git reflog                   # 查看所有 HEAD 移动记录（含被 reset/checkout 覆盖的提交）
git fsck --lost-found        # 找游离（dangling）的 commit
```

找到目标 commit hash 后，可：
- `git checkout <hash> -- <文件>` 恢复单个文件；
- `git merge <hash>` 或 `git cherry-pick <hash>` 合并整次提交。

---

## 4. 分支操作

```bash
git branch                   # 列出本地分支
git branch -a                # 列出所有分支（含远程）
git checkout -b <分支名>     # 创建并切换新分支
git checkout <分支名>        # 切换分支
git merge <分支名>           # 合并分支到当前分支
```

---

## 5. 远程操作

```bash
git push                     # 推送到当前分支的上游
git push origin main         # 显式推送到 origin 的 main 分支
git pull                     # 拉取并合并远程改动
git remote -v                # 查看远程仓库地址
```

---

## 6. 密钥安全自查

```bash
# 检查历史提交是否泄漏了敏感信息（API key、密码等）
git log -p --all | grep -i "sk-"
git log -p --all | grep -i "password"
```

**有输出** → 说明敏感信息进过历史，需轮换 key（历史无法真正删除，只能作废重发）。
**无输出** → 安全。

> 本项目约定：敏感信息（`DASHSCOPE_API_KEY`、`DB_PASSWORD`、`JWT_SECRET`）一律走环境变量注入，**禁止写进任何提交的文件**。`.gitignore` 已排除 `.env`、`application-local.yml`。

---

## 7. 常用场景速查

| 场景 | 命令 |
|---|---|
| 我改错了文件想重来 | `git checkout -- <文件>` |
| 我提交了但想改 message | `git commit --amend` |
| 我 add 错了想取消 | `git restore --staged <文件>` |
| 文件丢了想找回 | `git reflog` → `git checkout <hash> -- <文件>` |
| 不知道当前什么状态 | `git status` + `git log --oneline -5` |

---

## 8. 项目约定提醒（来自 CLAUDE.md）

1. AI **不执行任何 git 命令**，到达提交点时 AI 输出完整命令供你复制；
2. commit message 引用 `REQ`/`TC` 编号，保持代码可追溯；
3. 提交前先 `git status` 看清要提交什么，避免重演「只提交新文件、漏了修改文件」的版本错乱；
4. `target/` 是编译输出，已被 `.gitignore` 忽略，无需也不应提交。
