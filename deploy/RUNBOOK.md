# 润物细无声 · NURTURE — Release Runbook（运维手册）

> 适用：Staging 与 Production。本手册由 Release Implementation 轮建立，STAGING 收口轮统一为**双主机名架构**并随部署形态演进维护。
> 架构基线（双主机名，无 Worker）：
>
> ```
>   www.<域名>  ──▶ Cloudflare Pages（Angular SPA）
>   api.<域名>  ──▶ Cloudflare Tunnel ──出站反向连接──▶ Backend:8080（仅容器网）──▶ PostgreSQL（仅容器网）
>   Staging 对应：www-staging.<域名> / api-staging.<域名>
> ```
>
> **禁止**同一 hostname 同时作为 Pages 与 Tunnel 目标（DNS 记录只能指向一个目标，路径级分流需 Worker，本项目不引入）。

---

## 1. 备份（Backup）

- 脚本：`deploy/backup.sh`（pg_dump 自定义格式 `-Fc --no-owner`，附 `pg_restore --list` 完整性自检）
- 备份位置：`$NURTURE_BACKUP_DIR`（生产建议 `/var/backups/nurture`，独立数据卷或同步至对象存储；**严禁**与数据库同一块磁盘）
- 保留策略：每日备份最近 **7 份**；每周日归档周备份最近 **4 份**（脚本自动清理超期文件）
- 调度：Linux `crontab -e` → `0 2 * * * cd /opt/nurture/deploy && NURTURE_BACKUP_DIR=/var/backups/nurture ./backup.sh >> /var/backups/nurture/cron.log 2>&1`；Windows 任务计划程序每日 02:00
- 验收：`backup.log` 出现「备份完整性校验通过」；每月人工复做一次恢复演练（第 2 节）

## 2. 恢复（Restore Drill / 灾备）

- 脚本：`deploy/restore.sh <dump文件> <目标库>`
- **标准演练流程**（已在本机实测 PASS：备份 1 秒 / 恢复 2 秒 / RTO 55 秒就绪 / 10 张核心表逐表相等）：

```
① ./backup.sh                                     # 产出 daily/nurture-<ts>.dump
② ./restore.sh backups/daily/<dump> nurture_restore_drill_<ts>
③ NURTURE_DB_URL=jdbc:postgresql://localhost:5432/<演练库> SERVER_PORT=8081 \
   NURTURE_JWT_SECRET=<staging 值> java -jar backend/target/nurture-backend-1.0.0.jar
④ 核心业务验证：健康检查 200 → 测试账号登录 → 教师/订单/结算读取 → 非破坏性写入
⑤ 清理：docker exec deploy-postgres-1 psql -U nurture -d postgres -c 'DROP DATABASE "<演练库>"'
```

- 纪律：**生产数据禁止用于演练**；恢复操作前先停止向目标库写入。
- RTO/RPO 现状（实测）：RTO ≈ 55s 就绪 / ≈110s 验证完成；RPO = 备份间隔 ≤ 24h。

## 3. 回滚（Rollback）

### 3.1 应用发布失败（后端）
```
① 发布时镜像 tag 用 <git-sha>，禁止 latest 覆盖——保留上一版本 tag
② 将 backend 镜像指回上一 tag 后：docker compose -f docker-compose.yml -f docker-compose.prod.yml [-f docker-compose.staging.yml（仅 Staging）] up -d --no-deps backend
③ 健康检查通过后 Tunnel 自动恢复路由
```

### 3.2 前端发布失败
```
Cloudflare Pages → 项目 → Deployments → 上一个成功部署 → Rollback；验证首页与登录链路
```

### 3.3 数据库迁移失败（Flyway）
```
铁律：禁止手工改库结构。
① 区分「校验失败」与「迁移执行失败」（看启动日志）
② 迁移执行失败：先在演练库（第 2 节）验证修复方案 → 修复脚本后新版本部署（按 Flyway 规范处理 failed 状态）
③ 已提交但业务不可回退：应用回滚旧镜像（validate 只增不删通常兼容）
④ 数据损坏：走第 2 节恢复流程
```

### 3.4 备份恢复
见第 2 节。恢复期间停机，恢复后执行第 9 节冒烟再放行 Tunnel。

## 4. 生产 / Staging 初始化清单（首启必做）

| # | 事项 | 方式 | 完成判据 |
|---|------|------|----------|
| 1 | 数据库容器启动 + Flyway V1–V8 | compose up 自动 | 日志 `Successfully applied N migrations` |
| 2 | 运营管理员账号 | env `NURTURE_ADMIN_PHONE/PASSWORD` 自动创建 | 日志出现引导成功；该账号可登录 admin |
| 3 | 修改运营初始密码 | 后台改密 | 新密码可登录、旧密码失效 |
| 4 | 平台抽成核对 | V2 预置 platform_fee_rate=0.15；后台 PlatformConfig 可覆盖 | 值与商务约定一致 |
| 5 | 首批导师入驻 | 导师注册 → 提交资质 → 后台审核通过 | 出现在教师检索结果 |
| 6 | 学科/学段字典 | 由导师发布在售条目自然生成（无中央字典表） | 检索筛选维度可用 |
| 7 | 双运营账号验证 | `createAdminAccount` 编号缺陷已修复（nextAccountNo） | 第二个运营账号可创建并登录 |
| 8 | 基础配置核对 | env：API 主机名、CORS、JWT、沙箱开关、APIDOCSPUBLIC=false | `verify_config.py` 全部通过 |

## 5. Cloudflare 接入（双主机名架构）——**全部为人工操作清单**

**纪律：不将任何 token 写入源码；`CLOUDFLARE_TUNNEL_TOKEN` 仅经环境变量注入。以下 10 项全部需要由项目方在 Cloudflare 控制台人工执行，平台不代操作、不虚构完成状态。**

| # | 人工操作 | 操作位置 | 要点 / 验证 |
|---|---|---|---|
| 1 | 添加域名 | Cloudflare 控制台 → Add a site | 按提示选择套餐 |
| 2 | DNS 接管 | 域名注册商处修改 NS 为 Cloudflare 分配的 NS | `dig NS <域名>` 返回 cloudflare NS |
| 3 | 创建 Pages 项目 | Workers & Pages → Create → Pages → 连接 Git 仓库 | 构建命令 `npm run build:pages`；**输出目录 `dist/nurture-frontend/browser`**；构建变量 `NURTURE_API_HOST=api-staging.<域名>`（Staging）/ `api.<域名>`（生产） |
| 4 | 绑定自定义域 | Pages 项目 → Custom domains → 添加 `www-staging.<域名>` | Pages 自动创建 CNAME；HTTPS 证书自动签发 |
| 5 | 创建 Tunnel | Zero Trust → Networks → Tunnels → Create tunnel | 复制 token 填入 env 文件（与源码隔离） |
| 6 | 配置公共主机名 | Tunnel → Public Hostname → 添加 `api-staging.<域名>` | service 填 `http://backend:8080` |
| 7 | Tunnel 指向确认 | 同上 | 容器网络内 `cloudflared → backend:8080`，无公网入站端口 |
| 8 | SSL/TLS 模式 | SSL/TLS → Overview → Full (strict) | 边缘到源全程加密 |
| 9 | WAF | Security → WAF → 启用托管规则集 | 试运营期保持默认托管规则 |
| 10 | DNS 检查 | DNS → Records | `www-staging` CNAME 指向 pages 域；`api-staging` CNAME 指向 `<tunnel-id>.cfargotunnel.com` |

> Production 使用独立 Tunnel 与 `www.<域名>` / `api.<域名>`，操作同上，构建变量换为生产值。

## 6. 支付上线模式（P0-3 决策记录）

| 模式 | 说明 | 当前状态 |
|------|------|----------|
| A. Sandbox / Demo | 沙箱模拟端点跑通支付联动；仅限内测演示 | 代码就绪，Staging 开启 |
| **B. 运营线下收款确认（当前产品默认）** | 运营核验银行转账/收款码到账后调用 `POST /api/v1/admin/orders/{orderId}:confirm-offline-payment` 标记已付；资金不经平台网关 | **已实现并实测**（ADMIN 护栏 + 幂等 + 结算/通知 + 审计；6 并发恰一次状态迁移） |
| C. 微信/支付宝真实支付 | **待决策**，不自行接入 | 需：商户号、API 密钥、回调域名、资质；按既有 `PaymentGateway` 端口新增适配器 |

模式 B 操作流程：家长线下转账 → 运营后台核对到账 → 调用确认端点 → 订单 PAID + 结算 + 双方通知 + 审计（`payment.offline-confirm`）。

## 7. Staging 部署 Checklist（严格执行顺序）

| 步 | 事项 | 前置条件 | 操作 | 验证 | 失败处理 |
|---|---|---|---|---|---|
| 1 | Linux VM | 项目方提供 2C4G+ 机器 | 初始化系统、防火墙（仅放行 SSH） | SSH 可达；`ufw status` 仅 22 | 换机器或开放临时端口后收紧 |
| 2 | Docker | VM 就绪 | 安装 Docker Engine + Compose v2 | `docker compose version` | 换国内镜像源重装 |
| 3 | 发布文件 | 仓库/部署包到位 | 拉取仓库或解压部署包至 /opt/nurture | 目录结构含 deploy/ 与 backend/ | 校验包完整性 |
| 4 | staging.env | 第 2 步 | 按 `env.staging.example` 填真实值（root:600） | `grep -c '<域名>' staging.env`=0（占位已替换） | 缺项会导致 compose 拒启（:? 校验） |
| 5 | PostgreSQL | env 就绪 | `docker compose -f docker-compose.yml up -d postgres` | `pg_isready` 健康 | 查容器日志卷权限 |
| 6 | Flyway | 库启动 | 随后端自动执行 | 启动日志 `Successfully applied` V1–V8 | 校验失败看具体版本，禁手改库 |
| 7 | Backend | 库健康 | 叠加 prod 覆盖层启动 | `docker compose ps` backend healthy | 看 `docker logs`；密钥缺失即拒启 |
| 8 | Healthcheck | backend 启动 | 编排内置 | `curl 容器网 /actuator/health` = 200 UP | 排查 DB 连通与探针间隔 |
| 9 | Cloudflared | tunnel token 注入 | 随 compose 启动（依赖 backend healthy） | cloudflared 日志连接注册成功 | 检查 token 与主机名配置 |
| 10 | Pages 前端 | 仓库 + 构建变量 | 第 5 节人工操作 3–4 | 自定义域可访问 SPA | 检查构建变量与输出目录 |
| 11 | DNS | 前两域名绑定 | 第 5 节操作 10 | `dig` 两条 CNAME 正确 | 等待 NS/缓存传播 |
| 12 | HTTPS | 边缘配置 | Full (strict) | 浏览器证书有效 | 检查 SSL 模式 |
| 13 | Cookie | 登录链路 | 浏览器登录 | Set-Cookie 含 HttpOnly + Secure + SameSite=None | 检查 X-Forwarded-Proto 透传 |
| 14 | CORS | 双主机名生效 | 前端实际调用 API | 无 CORS 报错；预检通过 | 核对 env 的 CORS 精确源 |
| 15 | Swagger | api 域名 | 匿名访问契约与调试 UI | 403 拒绝（APIDOCSPUBLIC=false） | 检查 env 开关 |
| 16 | 8080 | VM 公网 IP | `nmap -p 8080 <IP>` | closed/filtered | 收紧防火墙、确认 ports 覆盖生效 |
| 17 | 5432 | VM 公网 IP | `nmap -p 5432 <IP>` | closed/filtered | 确认未叠加 dev 覆盖层 |

## 8. Staging 安全验收清单（14 项，部署后逐项勾选）

```
[ ] Cookie HttpOnly           —— 登录响应 Set-Cookie 含 HttpOnly；document.cookie 读不到令牌
[ ] Cookie Secure             —— HTTPS 下 Secure 位存在
[ ] SameSite 正确             —— 跨源 Pages→API 为 SameSite=None；同源形态为 Lax
[ ] CORS 精确允许 www-staging —— 合法源预检返回 allow-origin
[ ] evil Origin 被拒绝        —— https://evil.example 预检 403 且无 allow-origin 头
[ ] 8080 外网不可达           —— nmap VM 公网 8080 closed/filtered
[ ] 5432 外网不可达           —— nmap VM 公网 5432 closed/filtered
[ ] Swagger 匿名访问被拒绝    —— api 域名契约/调试 UI 匿名 403
[ ] OTP 日志无验证码          —— 触发一次短信发送，日志无 6 位数字形态（SmsCodeStoreLogSafetyTest 已覆盖源码侧）
[ ] Health 不泄漏敏感信息     —— /actuator/health 仅 {"status":"UP"}，无 components/env
[ ] Token 不进 localStorage   —— 前端仅存会话摘要；Application 面板无令牌
[ ] Agent Token 不泄露        —— 库中仅加盐哈希；伪造 Token 被拒（E2E 已覆盖）
[ ] Idempotency-Key 防重复写  —— 缺键写操作 400；同键重放同一响应
[ ] 跨站写请求被正确拒绝      —— 无自定义头的跨站表单写操作被拒（幂等头即 CSRF 防线）
```

## 9. Staging 人工 Smoke Test（14 步）

注册 → 登录 → 找老师 → 预约 → 改期 → 家长绑定 → 下单 → 支付模式 B（运营确认）→ 进入课堂 → 结课 → 考勤 → 反馈 → 评价 → 后台。逐步 PASS/FAIL 留档；失败按 RUNBOOK 对应章节排查，禁止以跳过代替通过。

## 10. Staging E2E

```
① 修改 tools/e2e_test.py 顶部 BASE 为 https://api-staging.<域名>/api/v1（或以环境变量参数化后传入）
② python3 tools/e2e_test.py          # 114 项全过为 PASS
③ python3 tools/probe_authz.py       # 19 项越权探测 0 漏洞
④ bash deploy/sql/cleanup_e2e_data.sql 清理测试数据
```
失败分类：环境问题 / 前端 API Host / Cookie / CORS / 后端 / 数据库 / 业务逻辑——禁止修改业务逻辑掩盖环境问题。

## 11. 部署包

`bash deploy/package-staging.sh` 生成时间戳化部署包（后端 jar + 全部部署配置与脚本 + 本手册）。前端不经部署包发布——必须经 Cloudflare Pages 以构建变量 `NURTURE_API_HOST` 从源码构建（防止带错误域名的静态产物流出）。
