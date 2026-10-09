# PROJECT_HANDOFF — NURTURE / 润物细无声 · 项目续作入口

> **新模型 / 新会话必读。** 本文件是项目跨模型续作的唯一入口。读完本文件即可恢复工作状态。
> 本文件只保留最关键信息与引用；细节在 `docs/` 专项文档与滚动事实源中。

---

## 1. 项目基本信息

| 项 | 内容 |
|---|---|
| 项目 | 润物细无声 / NURTURE — 线上家教与学习服务平台 |
| 目标 | 家教交易（找老师→预约→支付→课堂）+ 学习过程管理（任务/记录/周报/答疑）+ 成长陪伴 |
| 技术栈 | Angular 19 + TypeScript · Java 25 + Spring Boot · PostgreSQL 17 · JPA/Hibernate · Caffeine · Flyway · Docker · Cloudflare Pages/Tunnel |
| 架构 | 模块化单体 + Package-by-Feature（16 业务模块 × api/app/domain/infra 四层）+ ArchUnit 边界守门 |
| 当前阶段 | 代码与质量就绪，**Staging 门前**（配置/部署包/Runbook 就绪，等 VM/域名/Cloudflare 外部资源） |
| 硬约束 | 禁：微服务化、Redis/Kafka/K8s/OpenSearch、自研 RTC、改品牌、改深色主题、改既有核心交易链路、大模型 API 接入（本轮起） |
| 事实源 | `E:\线上家教平台\项目交接.md`（滚动维护六轮+记录，先读它的「当前进度」节） |

## 2. 文档地图（按此顺序读）

| 文件 | 用途 | 何时读 |
|---|---|---|
| `E:\线上家教平台\项目交接.md` | **滚动事实源**：六轮+维护记录、每轮全部细节、陷阱清单 | 接手时先读「二、当前进度」与「三、下一步」 |
| `docs/PROJECT_CONTEXT.md` | 产品背景 / 需求 / 用户硬约束 / 禁止事项 | 涉及产品决策时 |
| `docs/ARCHITECTURE.md` | 真实目录结构 / 模块清单 / 数据流 / 横切能力 | 改后端前 |
| `docs/DECISIONS.md` | 关键技术决策 + 原因 + 被否决方案 | 做架构/选型判断时 |
| `docs/CHANGELOG.md` | 按日期的变更与验证记录 | 了解某功能何时/如何落地 |
| `deploy/RUNBOOK.md` | 部署 / 备份恢复 / 回滚 / 初始化 / Cloudflare 人工操作 | 部署相关 |
| `docs/FEATURE_ROADMAP.md` | 功能增补路线图（缺口复核 / A-F 新功能评估 / 四阶段顺序） | 规划下一批功能时 |
| `README.md`（功能根目录） | 构建与启动命令速查 | 启动环境时 |

## 3. 当前开发进度总表（2026-10-09 第二轮核实）

状态图例：✅已完成且已验证 · 🟡已实现未充分验证 · 🔶部分实现 · 🔵规划中 · ⛔阻塞

### 版本保护（2026-10-09 新增）
| 项 | 状态 | 说明 |
|---|---|---|
| 本地 Git 仓库 | ✅ | 基线 `9631f8d`（480 文件）+ G3 `7979522` + G7 `3f6da7d`；identity 本地署名，**无远程仓库**（恢复能力=commit 历史本身） |
| .gitignore | ✅ | 排除 node_modules/dist/target/日志/deploy 备份与部署包/.env（.env.example 反排除保留）/运行期上传文件 backend/data/files/（历史已跟踪文件不受影响）/__pycache__ |
| 凭据核查 | ✅ | application.yml 密钥全部 `${VAR:dev默认}` 注入，仓库内仅开发占位值；无硬编码真实凭据 |

### 交易与履约闭环（第一梯队，全部 ✅）
| 功能 | 状态 | 验证依据 |
|---|---|---|
| 认证（注册/登录/Cookie/令牌轮换/锁定零窗口） | ✅ | IT + E2E；HttpOnly 实测 |
| 预约/改期/两阶段取消 | ✅ | BookingRescheduleIT + E2E |
| 监护绑定授权下单（含学生确认绑定入口 G3） | ✅ | GuardianBindingOrderAuthIT（含 G3 越权/幂等用例）+ E2E §8.3 + 浏览器全链路走查 |
| 订单/支付（沙箱 + 模式 B 线下收款；双端下单支付界面 G7） | ✅ | OfflinePaymentStateMachineIT 8 例 + ConcurrencyIT + **OrderPaymentFlowIT**（可下单视图/越权/一课一单/payer 隔离/无余额回滚/支付联动/防重复）+ E2E §8.4 + 浏览器全链路走查 + 生产模式冒烟（sandbox=false） |
| 退款/结算/评价/站内信/课程提醒 | ✅ | E2E 覆盖 |
| 课堂（外部会议链接 + 结课考勤） | ✅ | E2E 课堂链路 |

### 学习成长（2026-10-09 更新）
| 功能 | 状态 | 验证依据 | 剩余 |
|---|---|---|---|
| 学习成长中心 `/student/growth` | ✅ | `LearningGrowthIT` 真实数据聚合 + 无关学生隔离用例 | — |
| 课后任务全生命周期 | ✅ | `LearningGrowthIT`：创建→提交→重复提交 409→反馈→COMPLETED→再反馈 409→逾期 OVERDUE_PENDING→非归属教师 403×2 | — |
| 学习档案 `/student/archive` | ✅ | `LearningGrowthIT`：本人 ✓ / 授课教师 ✓ / 无关教师 403 | — |
| 家长周报（API+入口） | ✅ | `LearningGrowthIT` 周报权限 + E2E §17（六字段/无绑定 403）+ 浏览器实测；**本轮修复 studentId 档案 ID→账户 ID 语义缺陷** | — |
| 教师课堂记录 | 🟡 | booking_id 唯一约束防重（IT 主链路间接覆盖） | 重复创建 409 专属断言 |
| 课后答疑（结构化） | ✅ | `LearningGrowthIT`：提问→无关教师回答 403→归属回答→关闭→CLOSED 再答 409→无关系提问 403 | — |
| 教师收藏 toggle | ✅ | `LearningGrowthIT` toggle 幂等用例 | — |
| 规则化推荐 | 🟡 | 纯规则计算，无虚构指标 | 补价格过滤断言 |
| 三端前端界面（growth/archive/questions） | ✅ | 生产构建 + 9-30 四端浏览器走查 | — |

### 阶段一收口（2026-10-09 本轮，全部 ✅）
| 项 | 验证依据 |
|---|---|
| G9 `LearningGrowthIT` 9 用例（IT 19→28） | verify 全绿，旧 IT 零回归；抓出并修复周报 ID 语义缺陷 |
| G1 运营「订单与收款」卡（补 `GET /admin/orders/pending-payment`；修 ngOnInit 漏调 `loadPendingOrders()`） | E2E +3 + 浏览器实测：列表→二次确认→PAID→移出列表 |
| G2 家长「学习周报」入口 | E2E +3 + 浏览器实测展开面板真实数据 |
| G4 学生「评价」入口（补 `GET /student/me/lesson-history` + `GET /review/me/reviews-given`；修 tags 懒加载 500 与课程表不含已完成课断点） | E2E +2 + 浏览器实测表单/重复评价 409 提示/✓已评价标记 |
| G8 「进入课堂」（协议白名单 + 弹窗拦截降级） | E2E §15.3 + 浏览器实测「课堂尚未就绪」降级提示 |

### 安全与工程（全部 ✅）
| 功能 | 状态 | 验证依据 |
|---|---|---|
| 授权漏洞修复 | ✅ 19 项 | probe_authz.py 多轮 0 漏洞 |
| 六层测试体系 | ✅ | 单元 35 / 集成 30 / E2E 160 / 探针 / ArchUnit 空基线 / 配置门禁 24 项 |
| 备份恢复 | ✅ 实弹演练 | RTO 实测 55 秒（deploy/backup.sh + restore.sh） |
| 健康检查 | ✅ | actuator + probes + compose healthcheck + cloudflared 依赖 |
| 答辩 PPT / 计划书 / 小白指南 | ✅ | E:\线上家教平台\ 下三份 docx + pptx |

### 未完成 / 待决策（⛔/🔵）
⛔ Staging 部署（等：VM、域名、Cloudflare 账号——全部外部资源）
🔵 支付上线模式决策（A 沙箱 / B 线下收款【已实现】/ C 真实渠道【需商户资质】）
🔵 课堂形态决策（外部会议【当前】/ RTC SDK）
🔵 教育合规（ICP / 协议 / 未成年人条款）——正式运营最大前置
🔵 生产备份调度、AI 上层能力（均规划中）

## 4. 当前工作现场（2026-10-09 第二轮快照）

- **最近完成**：版本保护（git 基线 9631f8d + 分阶段提交）+ G3 学生确认监护绑定入口 + G7 学生/家长下单支付界面；新增 `OrderPaymentFlowIT`（IT 28→30）与 E2E §8.3/§8.4（124→160）；G3/G7 浏览器全链路走查通过；生产模式冒烟（NURTURE_PAYMENT_SANDBOX_ENABLED=false → config=false、沙箱端点下线）。
- **已知未收口**：G5/G6/G10（界面层缺口，见 ROADMAP）；Staging 等外部资源；学生自建订单只能经运营线下确认收款（学生钱包无充值通道，设计如此）。
- **运行状态**：本轮结束时后端（8080，沙箱模式）/前端（4200）/PostgreSQL 均在运行中。
- **产物新鲜度**：jar 与前端 dist 均为 2026-10-09 本轮重建（晚于全部源码修改）✓。
- **版本管理**：**本地 git 仓库已建立**（identity 署名 NURTURE Dev，无远程）。恢复基线=`git log`/`git checkout <commit>`；仍严禁 reset --hard/clean -fd/强制推送；不加远程不推送。
- **无失败测试遗留**：最近一次 verify 全绿（单元 35 + IT 30）。

## 5. 当前运行与验证方式（全部已验证）

```bash
# 0. 前提：Docker Desktop 已启动（开始菜单 → Docker Desktop，等鲸鱼稳定）
# 1. 数据库
docker start deploy-postgres-1
# 2. 后端（8080；同时注入运营测试账号）
export JAVA_HOME="C:/Users/lxs/apps/jdk-25.0.4.1+1"
cd "E:/线上家教平台/线上家教平台（功能）/backend"
NURTURE_ADMIN_PHONE=13800000000 NURTURE_ADMIN_PASSWORD=Admin#2026 \
  "$JAVA_HOME/bin/java" -jar target/nurture-backend-1.0.0.jar
# 3. 前端（4200 + 局域网）
cd ../frontend && npm start -- --host 0.0.0.0
# 测试账号：学生 13900000002/Student#2026 · 家长 13900000003/Parent#2026
#           教师 13900000001/Teacher#2026 · 运营 13800000000/Admin#2026（仅本机）
# 全量回归（先杀 java：taskkill //F //IM java.exe）
mvn -B -s C:/Users/lxs/apps/maven-settings.xml -Pintegration verify   # 单元+ArchUnit+IT
python3 tools/e2e_test.py        # 160 项 E2E（需服务已启动）
python3 tools/probe_authz.py     # 越权探针
python3 deploy/verify_config.py  # 24+ 项配置门禁
npx ng build --configuration production   # 前端生产构建（frontend/ 下）
```

## 6. 新模型接手指南（按顺序执行）

1. 读本文件 → 2. 读 `项目交接.md` 的「当前进度」+「下一步」两节 → 3. 按需读 docs/ → 4. 核实：`ls backend/src/main/resources/db/migration/` 应为 V1–V10；`netstat` 看服务状态 → 5. **禁止**：重置/清理、改历史 Migration、动核心交易链路、引入禁用组件 → 6. 从第 7 节「下一步」第一项开始 → 7. 完成任务后回写交接文档（规则见 `AGENTS.md`）。

**历史陷阱速查**（完整版见交接文档末尾）：Docker 迁移 SQL 忌 min(uuid)；ArchUnit 须 1.5.1（Java 25）；匿名访问统一 403 非 401；越权探针写操作必须带幂等键；改迁移后必须重新 package；CSS `::after` 在产物中会被简写；e2e 的 notifications 返回键是 `items`；档期 DELETE=置 BLOCKED 非物理删。

## 7. 下一步开发计划（按优先级，2026-10-09 更新）

| 优先 | 任务 | 为什么 | 首查文件 | 验收 |
|---|---|---|---|---|
| ~~P0-1~~ | ~~learning 专属 IT~~ ✅ 2026-10-09 完成（LearningGrowthIT 9 用例） | — | — | — |
| ~~P1-1~~ | ~~G7 下单支付界面~~ ✅ 2026-10-09 完成（commit 3f6da7d） | — | — | — |
| ~~P1-2~~ | ~~G3 学生确认绑定入口~~ ✅ 2026-10-09 完成（commit 7979522） | — | — | — |
| **P1-3** | ROADMAP G5/G6：教师资质上传 + 定价管理界面 | 教师自助入驻与经营 | `teacher-dashboard.component.html` | 走查 + E2E |
| P2 | 首页五步模块、匹配页收藏/时间筛选（G10）、附件上传 | 体验补全 | 见 ROADMAP | 各项验收标准 |
| ⛔ | Staging 部署 | 等外部资源（VM/域名/CF） | `deploy/RUNBOOK.md` | 14 项验收全过 |

## 8. 风险与限制

- ~~无版本控制~~ **已解决（2026-10-09）**：本地 git 仓库已建立（9631f8d 起三个 commit）。仍存风险：**无远程仓库**，机器盘故障仍会丢失全部历史——如需异地备份由用户决定是否添加远程（未经授权不得擅自添加/推送）。
- learning 模块在 ArchUnit 空基线下新增——若模块间出现违规依赖，verify 会红灯（这是设计行为）。
- 本文档核实时间 2026-10-09（第二轮）；此后若有会话修改代码而未回写本文档，以实际代码为准并**补记差异**。
