# 润物细无声 · Nurture — 线上学术精修与课后答疑平台

> 因材施以微雨，静候学海生花

全栈实现 · 领域驱动设计 (DDD) · 模块化单体 · AI Agent 双客户端平等模型 · ADHD 友好交互

当前交付状态（2026-10-10）：核心旅程与退款红冲增量已通过本地隔离验收，真实短信/支付与完整Staging仍待外部资源。续作先读 [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md)，本机最新启动命令见其§5；本轮三份交付报告与剩余功能清单位于 `docs/`。历史业务库仍V10，新代码启动会应用V11/V12，开发验收必须显式使用隔离库，不直接升级历史库。

---

## 一、仓库结构

```
├── frontend/                 # Angular 19 + TypeScript（人类 Web 客户端）
│   └── src/
│       ├── styles/           # Design System：tokens（墨绿/米白/浅灰绿/暖金）+ base（无障碍基线）+ components（容器/区块/按钮/卡片原语）
│       └── app/
│           ├── core/         # 统一鉴权、API 客户端（Idempotency-Key 拦截器）
│           ├── layout/       # site-header 吸顶导航（移动端无障碍菜单）/ site-footer
│           ├── shared/       # models（TeacherSummary 对齐 REST 契约）/ teacher-card / reveal 滚动显现指令
│           └── features/
│               ├── landing/  # 首页：Hero → 理念 → 服务 → 导师 → 学科 → 流程 → 心声 → 愿景 → CTA（规范锁定文案）
│               ├── auth/     # 统一多角色登录门户（学生/家长/导师/运营 四 Tab）
│               ├── match/    # 找老师（筛选+需求发布双入口、预约分步向导）
│               ├── teachers/ messages/ refunds/ # 真实教师详情/对比、咨询、退款申请进度
│               └── student|parent|teacher|admin/  # 四大角色控制台
├── backend/                  # Java 25 + Spring Boot 3.5 模块化单体
│   └── src/main/java/com/nurture/
│       ├── shared/           # 内核：security / idempotency / audit(含Agent写审计) / outbox / twophase / storage / mcp / config
│       └── 17 个业务模块       # identity student parent teacher verification matching(含需求发布)
│                              # scheduling booking classroom(含考勤) order payment refund settlement messaging review admin learning
│           └── api|app|domain|infra   # Package-by-Feature 分层
├── deploy/docker-compose.yml # PostgreSQL + 后端容器 + Cloudflare Tunnel
├── backend/Dockerfile        # 标准 OCI 单体镜像（严禁 K8s / Service Mesh）
└── .github/workflows/        # 前后端 CI（测试/构建/OCI 镜像；Pages 托管见下）
```

## 二、快速启动（本地开发）

### 1. 数据库（PostgreSQL 17）

```bash
cd deploy
docker compose up -d postgres
```

### 2. 后端（需要 JDK 25 + Maven 3.9）

```bash
cd backend
NURTURE_JWT_SECRET=$(openssl rand -hex 32) \
NURTURE_ADMIN_PHONE=13800000000 \
NURTURE_ADMIN_PASSWORD='Admin#2026' \
mvn spring-boot:run
```

启动后自动执行当前 Flyway V1–V12（新V11=退款红冲、V12=会话唯一），并创建初始运营管理员。下面示例应显式设置 `NURTURE_DB_URL` 指向隔离开发数据库；本次Windows已验证命令以PROJECT_HANDOFF §5为准。

- OpenAPI 3.0 契约：http://localhost:8080/api/v1/api-docs
- Swagger UI：http://localhost:8080/api/v1/swagger-ui

### 3. 前端（需要 Node 20+）

```bash
cd frontend
npm install
npm start          # http://localhost:4200，/api 已代理到 8080
```

### 4. 一键容器化交付（生产形态）

```bash
cd deploy
NURTURE_JWT_SECRET=<随机32字节hex> \
NURTURE_ADMIN_PASSWORD=<强密码> \
CLOUDFLARE_TUNNEL_TOKEN=<Tunnel令牌> \
docker compose up -d --build
```

后端与数据库端口均不向公网暴露，仅经 Cloudflare Tunnel 出站反向连接；
前端构建产物（`ng build`）托管于 Cloudflare Pages。

## 三、架构决策速览（与工程规范书逐条对应）

| 规范要求 | 实现位置 |
| --- | --- |
| 新中式学术美学色彩 Token（竹青/暖檀/宣纸白等） | `frontend/src/styles/tokens.css`（规范锁定值） |
| 衬线/无衬线字体栈、cubic-bezier(0.16,1,0.3,1)、阶梯错落入场 | `tokens.css` + `base.css` |
| 首屏轻奢微胶囊/48px 主标题/暖檀标语/CTA 群/信任背书条 | `features/landing/*` |
| 多角色 Tab 统一登录门户、双通道登录、ADHD 就地容错 | `features/auth/*` |
| 16 模块 Package-by-Feature，物理包结构隔离 | `backend/src/main/java/com/nurture/<module>/` |
| 家长与学生实体物理分离（独立账户 + 监护绑定） | `identity/domain/Account` + `student/domain/GuardianBinding` |
| 禁 Redis：Caffeine 本地缓存 | `pom.xml` + `identity/app/SmsCodeStore` |
| 禁 Kafka：Spring 进程内事件 + 事务发件箱 | `shared/outbox/*` |
| 禁 Elasticsearch：pg_trgm + GIN 全文索引 | `V1__nurture_core_schema.sql` + `SubjectOfferingRepository` |
| 禁自建对象存储：FileStorageService 端口 + 本地卷实现 | `shared/storage/*` |
| 禁自研 WebRTC：外部会议 SaaS 凭证转发 | `classroom/*` |
| Idempotency-Key 强幂等（唯一索引防重表） | `shared/idempotency/*` + `idempotency_keys` 表 |
| Preview/Execute 两阶段（退款、取消课时） | `shared/twophase/*` + `booking/*cancel` + `admin/refund-cases/*arbitration` |
| Agent 独立身份：专用 Token、仅存加盐哈希、Scope 最小权限 | `identity/domain/ApiToken` + `shared/security/Scope` |
| MCP 仅作协议适配层，严禁复刻业务规则 | `shared/mcp/*`（`/api/v1/mcp/tools` 工具发现） |
| 全量不可篡改审计（触发器拒绝 UPDATE/DELETE） | `shared/audit/*` + `audit_logs` 表 |
| ADHD 友好 / Apple HIG：44pt 热区、双通道状态、focus-visible、reduced-motion、专注模式、任务切块 | `styles/base.css` + `features/student/*` |
| 找老师双入口（搜索/筛选 + 需求发布并存） | `matching/*`（StudentDemand）+ `features/match/*` |
| 教师卡片信任证据（认证、评价评分/条数、最近可约时间） | `MatchingApplicationService` 候选增强 |
| 预约分步向导：步骤可见、保留上下文、冲突标记失效 | `features/match/*` 预约向导 |
| 当日课程 / 我的课程表 | `booking/app` todayLessons + 学生课程表 |
| 考勤状态（正常/迟到/缺席，供退款结算争议取证） | `classroom/*` attendance |
| 运营账户管理（锁定/解锁，落审计） | `admin/*` accounts |
| Agent 写操作全量审计（过滤器自动落库） | `shared/audit/AgentWriteAuditFilter` |
| OpenAPI 写接口标注幂等键/确认凭证参数 | `shared/config/OpenApiConfig` OperationCustomizer |
| CI/CD：后端 mvn verify + OCI 构建；前端 ng build；Pages 托管 | `.github/workflows/*` |

## 四、核心业务闭环

找老师（matching，透明推荐理由）→ 沟通/试听（messaging + booking TRIAL）→ 预约
（scheduling 并发排他 + booking 状态机）→ 付费（order + payment 回调防重入 + parent 钱包）
→ 上课（classroom 会议凭证 + 课前任务切块）→ 反馈（review 双向评价）
→ 复购（settlement 课酬结算 + 复购下单）。

高后果动作（取消预约、退款仲裁）强制 Preview → Execute 两阶段；全程落审计。

## 五、Agent 接入方式（双主权客户端平等模型）

1. 运营在 `POST /api/v1/identity/api-tokens` 签发 Agent Token（明文仅展示一次）；
2. Agent 以 `Authorization: Bearer nur_at_...` 调用与人类相同的 `/api/v1/*` 契约；
3. 所有写操作携带 `Idempotency-Key: <UUIDv4>`；
4. 退款/取消等先 Preview 获取短效 `confirmationToken`，再 Execute；
5. 工具发现：`GET /api/v1/mcp/tools`（仅协议适配，无业务规则）。
