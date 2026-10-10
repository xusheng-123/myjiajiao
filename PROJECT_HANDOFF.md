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

### 教师自助入驻与课程经营（2026-10-09 本轮，全部 ✅）
| 项 | 验证依据 |
|---|---|
| G5 教师「资质认证」卡自助上传（双文件 multipart、≤20MB 如实标注、防重、文件清单/移除、服务端状态与真实审核意见、驳回可重提） | E2E §3.5 +14（未提交→提交→驳回意见可见→重提新案例→通过）+ 浏览器全生命周期走查（DataTransfer 注入走真实 change 链路，刷新恢复服务端状态）；材料无查看端点保持不开放 |
| G6 教师「课程与定价」卡（列表/新增 D9 幂等如实提示/调价标注「仅影响新订单」/下架二次确认「停止销售」无重新上架入口） | E2E §8.6 +16（新增/幂等/非法价 4xx/调价以服务端返回为准/他人调价·下架 403/新订单按新价 18000/已生成订单快照不变/下架列表消失/检索不可见/回落默认价 30000）+ 浏览器走查（空态→拦截→新增→幂等→调价 199.50→下架） |
| E2E 三处多轮累计假设修复（G7 钱包断言改用充值前余额/账户锚定翻页/G1 待收款翻页） | 修复后三轮 E2E 全绿；属测试健壮性修复，业务代码零改动 |
| 后端改动 | **零**（G5/G6 全部复用既有端点与状态机；不新增审核状态、不改定价快照与结算规则） |

### 安全与工程（全部 ✅）
| 功能 | 状态 | 验证依据 |
|---|---|---|
| 授权漏洞修复 | ✅ 19 项 | probe_authz.py 多轮 0 漏洞 |
| 六层测试体系 | ✅ | 单元 35 / 集成 32 / E2E 205 / 探针 / ArchUnit 空基线 / 配置门禁 24 项 |
| 备份恢复 | ✅ 实弹演练 | RTO 实测 55 秒（deploy/backup.sh + restore.sh） |
| 健康检查 | ✅ | actuator + probes + compose healthcheck + cloudflared 依赖 |
| 答辩 PPT / 计划书 / 小白指南 | ✅ | E:\线上家教平台\ 下三份 docx + pptx |

### 未完成 / 待决策（⛔/🔵）
⛔ Staging 部署（等：VM、域名、Cloudflare 账号——全部外部资源）
🔵 支付上线模式决策（A 沙箱 / B 线下收款【已实现】/ C 真实渠道【需商户资质】）
🔵 课堂形态决策（外部会议【当前】/ RTC SDK）
🔵 教育合规（ICP / 协议 / 未成年人条款）——正式运营最大前置
🔵 生产备份调度、AI 上层能力（均规划中）

## 4. 当前工作现场（2026-10-10 第五轮快照：质量加固）

- **最近完成**：阶段一收官后的质量加固轮（四项风险全部核实并有证据地处置）——①**下架课程定价缺陷确认并修复**：`offeringsPriceOf` 只含在售条目导致「明确存在但已下架」的科目下单回落 ¥300 默认价（G6 E2E 曾把该行为断言为预期）；修复为定价三分支（在售→现价 / 同科目存在下架条目→409 拒绝 / 从未定价→保留 ¥300 兜底），`DelistedCoursePricingIT` 覆盖，G6 E2E 断言更新。②**过期档期后端过滤**：`openSlotsOf` 只返回未开始的 OPEN（open-slots 端点与检索 nextOpenSlotAt 共用）；新建预约对过去档期 400 拒绝（改期路径不变）；`StaleSlotGuardIT` 覆盖（历史档期仅教师日历可见，不物理删除）。③**资质上传白名单**：扩展名/MIME/文件魔数三重校验（PDF/JPG/PNG），伪造类型 400 拒绝；`max-request-size: 45MB`（此前缺省 10MB 使 10~20MB 合法文件必然失败）；超限经 IdempotencyFilter 拦截返回 413（识别 Tomcat 原生 FileSizeLimitExceededException），前端文案/accept 同步。④**窄屏布局首次真机走查**：Edge headless CDP 真实 375px 视口（`tools/narrow_walkthrough*.py`，登录态注入）——match 页/收藏面板/筛选空态零横向溢出、按钮无遮挡、chips wrap 正常。
- **测试基线**：单元 35 · IT 32（+2 新 IT）· E2E 205（+4 断言：G6 下架拒绝/兜底保留/白名单×2，改 1 断言：回落价→409）· 探针 0 · ng build ✓（match css 268B 警告既有）。
- **已知未收口**：试听预约可创建付费订单（G6 E2E 实测 300 元，与「试听免费」语义存在张力——**待产品决策**，本轮未动交易链路）；教师对比界面（F）；Staging 外部资源；match css 268B 预算警告。
- **运行状态**：本轮结束时后端（8080，沙箱模式，09:5x 新 jar）/前端（4200 ng serve）/PostgreSQL 均在运行中。
- **产物新鲜度**：jar 与前端 dist 均为 2026-10-10 本轮重建（晚于全部源码修改）✓。
- **版本管理**：本地 git 仓库（无远程，严禁 reset --hard/clean -fd/强推/擅加远程）。本轮基线：`bf8dee8` → `b867906`（质量加固，22 文件）。
- **无失败测试遗留**：最近一次 verify 全绿（单元 35 + IT 32）、E2E 205/205、探针 0。

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
python3 tools/e2e_test.py        # 205 项 E2E（需服务已启动）
python3 tools/probe_authz.py     # 越权探针
python3 deploy/verify_config.py  # 24+ 项配置门禁
npx ng build --configuration production   # 前端生产构建（frontend/ 下）
```

## 6. 新模型接手指南（按顺序执行）

1. 读本文件 → 2. 读 `项目交接.md` 的「当前进度」+「下一步」两节 → 3. 按需读 docs/ → 4. 核实：`ls backend/src/main/resources/db/migration/` 应为 V1–V10；`netstat` 看服务状态 → 5. **禁止**：重置/清理、改历史 Migration、动核心交易链路、引入禁用组件 → 6. 从第 7 节「下一步」第一项开始 → 7. 完成任务后回写交接文档（规则见 `AGENTS.md`）。

**历史陷阱速查**（完整版见交接文档末尾）：Docker 迁移 SQL 忌 min(uuid)；ArchUnit 须 1.5.1（Java 25）；匿名访问统一 403 非 401；越权探针写操作必须带幂等键；改迁移后必须重新 package；CSS `::after` 在产物中会被简写；e2e 的 notifications 返回键是 `items`；档期 DELETE=置 BLOCKED 非物理删。

## 7. 下一步开发计划（按优先级，2026-10-10 更新）

| 优先 | 任务 | 为什么 | 首查文件 | 验收 |
|---|---|---|---|---|
| ~~P0-1~~ | ~~learning 专属 IT~~ ✅ 2026-10-09 完成（LearningGrowthIT 9 用例） | — | — | — |
| ~~P1-1~~ | ~~G7 下单支付界面~~ ✅ 2026-10-09 完成（commit 3f6da7d） | — | — | — |
| ~~P1-2~~ | ~~G3 学生确认绑定入口~~ ✅ 2026-10-09 完成（commit 7979522） | — | — | — |
| ~~P1-3~~ | ~~ROADMAP G5/G6：教师资质上传 + 定价管理界面~~ ✅ 2026-10-09 完成（commit ae089af/02caacf/65c997e/0861d92） | — | — | — |
| ~~P1-4~~ | ~~ROADMAP G10：收藏 + 可约时间筛选~~ ✅ 2026-10-10 完成（commit 3b24d28，**阶段一全部收官**） | — | — | — |
| ~~P1-5~~ | ~~阶段一收官质量加固（四项风险核查与修复）~~ ✅ 2026-10-10 完成——①下架课程默认价缺陷修复（定价三分支，下单 409）②过期档期后端过滤（open-slots 排除过去 + 新建预约 400）③资质上传三重白名单 + max-request-size 45MB + 超限 413 ④窄屏 375px Edge headless CDP 真视口走查零溢出；新增 DelistedCoursePricingIT/StaleSlotGuardIT，E2E 201→205；待决策：试听预约可建付费订单（见交付报告） | — | — | — |
| P2 | 首页五步模块、附件上传 | 体验补全 | 见 ROADMAP | 各项验收标准 |
| P2 | 教师对比界面（最多 3 名并排，ROADMAP §「对比界面」） | 阶段二首项候选，全部数据字段已具备 | ROADMAP 对比方案（v1） | 走查 + E2E |
| P2 | 试听免费/付费语义决策后落码 | 试听预约当前可创建付费订单（实测 300 元），与「试听免费」语义张力待产品拍板；决策后最小改动触及交易链路，须单独立项 | 交付报告 §9、G6 E2E | 决策记录 + 对应实现 + 回归 |
| ⛔ | Staging 部署 | 等外部资源（VM/域名/CF） | `deploy/RUNBOOK.md` | 14 项验收全过 |

## 8. 风险与限制

- ~~无版本控制~~ **已解决（2026-10-09）**：本地 git 仓库已建立（9631f8d 起三个 commit）。仍存风险：**无远程仓库**，机器盘故障仍会丢失全部历史——如需异地备份由用户决定是否添加远程（未经授权不得擅自添加/推送）。
- learning 模块在 ArchUnit 空基线下新增——若模块间出现违规依赖，verify 会红灯（这是设计行为）。
- 本文档核实时间 2026-10-09（第二轮）；此后若有会话修改代码而未回写本文档，以实际代码为准并**补记差异**。
