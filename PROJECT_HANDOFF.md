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
| 架构 | 模块化单体 + Package-by-Feature（17 业务模块，含 learning；api/app/domain/infra 四层）+ ArchUnit 边界守门 |
| 当前阶段 | 核心旅程与退款红冲增量已验证，**Staging 门前**（配置/部署包/Runbook 就绪，等 VM/域名/Cloudflare 外部资源） |
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

## 3. 当前开发进度总表（2026-10-11 阶段5交付核实）

状态图例：✅已完成且已验证 · 🟡已实现未充分验证 · 🔶部分实现 · 🔵规划中 · ⛔阻塞

2026-10-10 用户明确授权担任负责人并逐步补缺；历史“四问待批准”不再阻断新增代码/隔离测试。用户确认没有短信服务商、支付商户、测试服务器和域名。历史业务数据没有自动订正。

| 本轮补缺 | 状态 | 新验证证据 |
|---|---|---|
| V11 全额退款红冲与并发防重 | ✅ | RefundReversalIT 12 例，取消/仲裁同事务、正向保留、非PENDING阻断、并发只退一次 |
| 历史订正只读审计与隔离演练 | ✅脚本 / 🔶业务库未执行 | dry-run强制回滚、重复跳过、钱包对账；主库仍V10 |
| 正式课预约 UI、真实公开教师详情、最多3人对比 | ✅ | PublicTeacherDiscoveryIT 5例 + 桌面/375px真浏览器 |
| 教师注册资料原子保存、资料编辑、DTO修复懒加载500 | ✅ | 注册断言 + 编辑后公开详情真实更新 |
| 咨询、未读/已读、最新100/更早分页、退款申请与进度 | ✅ | ConsultationAndMaterialIT 5例 + 双向咨询与家长订单退款浏览器走查 |
| 运营材料受控下载与审计、案例ID契约及两阶段退款UI | ✅ | 下载二进制比对、BOLA测试、运营实际预览/执行 |
| 短信不可用如实503/密码默认、家长付款旅程引导 | ✅ | SmsDeliveryFailureTest 3例、能力端点IT、相关订单归属与UI |
| 完整竞品能力、真实短信/支付、全栈部署 | 🔶 / ⛔ | 见 docs/补缺执行清单_2026-10-10.md，未完成项保持未完成 |

**八阶段执行进度（2026-10-11）**：阶段1–5✅。阶段5V17有限重复档期/真实冲突预览、跳过与当天时间例外、发布咨询锁、规则分页/刷新恢复、三角色ICS导出；不创建订单或批量扣款。最终46单元/架构+113IT零失败，E2E220/0、探针19拒绝/0漏洞、配置/构建及6桌面/375px三端旅程通过；主库仍V10。报告docs/交付报告_阶段5重复档期与日历_2026-10-11.md；阶段3/4报告保留。下一步阶段6运营/检索分页、重新上架、客服与首页治理，从V18起；阶段6–8未完成。

以下既有轮次表保留原验证范围；本轮最终回归基线为单元38+ArchUnit2（合计40）、IT65、E2E218/0、探针19项拒绝/0漏洞、配置门禁全过、production build通过。正式课、聊天、退款UI、材料查看及Mock默认问题已补齐，早期竞品静态评估不再代表当前状态。

### 版本保护（2026-10-09 新增）
| 项 | 状态 | 说明 |
|---|---|---|
| 本地 Git 仓库 | ✅ | 基线 `9631f8d`（480 文件）+ G3 `7979522` + G7 `3f6da7d`；identity 本地署名，**无远程仓库**（恢复能力=commit 历史本身） |
| .gitignore | ✅ | 排除 node_modules/dist/target/日志/deploy 备份与部署包/.env（.env.example 反排除保留）/运行期上传文件 backend/data/files/（历史已跟踪文件不受影响）/__pycache__ |
| 凭据核查 | ✅ | application.yml 密钥全部 `${VAR:dev默认}` 注入，仓库内仅开发占位值；无硬编码真实凭据 |
| Sites 前端预览 | ✅ | 私有 Site v2 已更新（部署succeeded）：`https://nurture-platform-preview.kelly-cooper-4963.chatgpt.site`；Angular 前端静态包，构建提交 `fac74ba3dfb1c496fa1402d2e0ce722674d8d769`。后端 API 未部署，页面已显式标注受影响功能 |

### 交易与履约闭环（第一梯队，全部 ✅）
| 功能 | 状态 | 验证依据 |
|---|---|---|
| 认证（注册/登录/Cookie/令牌轮换/锁定零窗口） | ✅ | IT + E2E；HttpOnly 实测 |
| 预约/改期/两阶段取消 | ✅ | BookingRescheduleIT（4，含过去目标 400/过去源补救）+ **RescheduleConcurrencyIT**（3 轮真实并发无孤儿 HELD）+ E2E |
| 监护绑定授权下单（含学生确认绑定入口 G3） | ✅ | GuardianBindingOrderAuthIT（含 G3 越权/幂等用例）+ E2E §8.3 + 浏览器全链路走查 |
| 订单/支付（沙箱 + 模式 B 线下收款；双端下单支付界面 G7；**试听免费闸门 2026-10-10**） | ✅ | OfflinePaymentStateMachineIT 8 例 + ConcurrencyIT + **OrderPaymentFlowIT**（可下单视图/越权/一课一单/payer 隔离/无余额回滚/支付联动/防重复）+ **TrialFreeRuleIT 2 例**（试听下单 409/orderable 排除 TRIAL/两阶段取消无订单）+ E2E §8.4/§8.6 + 浏览器走查 + 生产模式冒烟（sandbox=false） |
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
| G5 教师「资质认证」卡自助上传（双文件 multipart、≤20MB 如实标注、防重、文件清单/移除、服务端状态与真实审核意见、驳回可重提） | E2E §3.5 +14（未提交→提交→驳回意见可见→重提新案例→通过）+ 浏览器全生命周期走查（DataTransfer 注入走真实 change 链路，刷新恢复服务端状态）；材料当轮无查看端点；本轮已补受控运营下载 |
| G6 教师「课程与定价」卡（列表/新增 D9 幂等如实提示/调价标注「仅影响新订单」/下架二次确认「停止销售」无重新上架入口） | E2E §8.6 +16（新增/幂等/非法价 4xx/调价以服务端返回为准/他人调价·下架 403/新订单按新价 18000/已生成订单快照不变/下架列表消失/检索不可见/回落默认价 30000）+ 浏览器走查（空态→拦截→新增→幂等→调价 199.50→下架） |
| E2E 三处多轮累计假设修复（G7 钱包断言改用充值前余额/账户锚定翻页/G1 待收款翻页） | 修复后三轮 E2E 全绿；属测试健壮性修复，业务代码零改动 |
| 后端改动 | **零**（G5/G6 全部复用既有端点与状态机；不新增审核状态、不改定价快照与结算规则） |

### 安全与工程（全部 ✅）
| 功能 | 状态 | 验证依据 |
|---|---|---|
| 授权漏洞修复 | ✅ 19 项 | probe_authz.py 多轮 0 漏洞 |
| 六层测试体系 | ✅ | 当前单元38 + ArchUnit2 / IT65 / E2E218 / 探针19 / 配置门禁全过（2026-10-10） |
| 备份恢复 | ✅ 实弹演练 | RTO 实测 55 秒（deploy/backup.sh + restore.sh） |
| 健康检查 | ✅ | actuator + probes + compose healthcheck + cloudflared 依赖 |
| 答辩 PPT / 计划书 / 小白指南 | ✅ | E:\线上家教平台\ 下三份 docx + pptx |

### 未完成 / 待决策（⛔/🔵）
⛔ Staging 部署（等：VM、域名、Cloudflare 账号——全部外部资源）
🔵 支付上线模式决策（A 沙箱 / B 线下收款【已实现】/ C 真实渠道【需商户资质】）
🔵 课堂形态决策（外部会议【当前】/ RTC SDK）
🔵 教育合规（ICP / 协议 / 未成年人条款）——正式运营最大前置
🔵 生产备份调度、AI 上层能力（均规划中）

## 4. 当前工作现场（2026-10-11 阶段5最终快照）

阶段5草稿状态已由完整测试替代，当前运行最终隔离副本；旧PID均按命令核实后停止，未杀其他服务。

- **代码与迁移**：V1–V17，历史迁移未改。主库nurture仍V10；8080连nurture_gap_acceptance_phase5_20261011（V17），IT库V17，旧phase4/phase3/phase2副本保留。
- **测试**：07:57:16 Java25 verify BUILD SUCCESS，Surefire46（44单元+2ArchUnit）/Failsafe113；新9IT/3unit；Angular production成功；E2E220/0、探针19拒绝/0漏洞、配置全过；重复档期冲突/例外/刷新与三端ICS桌面375px6图通过，V17回滚15表旧列指纹一致。统计以日志为准，失败日志保留。
- **运行**：Docker deploy-postgres-1 healthy；8080 PID9336，4200 PID18036，5432 PID36760（实时核实），8081无本轮服务。
- **产物**：backend/target/phase5-runtime.jar SHA256 010E74AFF2F8B269094D4B0D6422C3D578CF94BADDCF6FFDF4AA24FB4A12C888；上传target/phase5-files；日志phase5-final-verify.log、phase5-{server,e2e,probe,config,migration}.log、phase5-browser-retry.log与frontend/phase5-final-build.log；截图.walk/recurring-schedule/。运行副本避Windows锁。
- **历史资金**：未对主库迁移或订正。已备份恢复nurture_gap_acceptance_20261010_legacy并完成V11/V12真实Flyway迁移；17会话/18消息/90结算保持。副本重查A类38条/¥6545，事务订正对账通过且ROLLBACK恢复全部资金指纹。主库仍V10，不能把副本演练视为实际订正；下一步制定受控执行/回退范围，不去掉脚本保护直接执行。
- **版本保护**：核心补缺已提交本地检查点 `b9c7ae42cf30a8fdb7bc9489dd7e5693545d55d6`（88文件），无远端；最新阶段提交以git log为准。接手前已有 `tools/settlement_walkthrough.png` 未跟踪，保持原样。禁止reset/clean。AGENTS迁移核实已更新V17、本地Git无远端；旧报告继续保留历史范围。
- **阶段1收尾**：备份deploy/backups/stage1_20261010；stage1订正隔离库V12已提交38笔负向条目，回退副本nurture_gap_acceptance_stage1_recovery_20261010保持V10并恢复318文件。主库指纹不变；阶段1当时8080连旧final验收库，当前已切phase5，阶段1的8081已停止。
- **交付报告**：`docs/交付报告_核心旅程补缺_2026-10-10.md`、`交付报告_退款红冲与账务安全_2026-10-10.md`、`交付报告_验收与交接_2026-10-10.md`，执行清单 `docs/补缺执行清单_2026-10-10.md`。
- **Sites**：私有静态前端预览v2部署succeeded，commit `fac74ba3dfb1c496fa1402d2e0ce722674d8d769`，保持原URL和owner-only，版本/部署ID见验收报告；不连接 Java/PostgreSQL，顶部说明接口操作不可用，不等同完整Staging。

## 5. 当前运行与验证方式（PowerShell，本轮实测）

先确认Docker Desktop与数据库容器健康、验收库已创建。禁止将测试命令指向历史主库；下面 phase5 库已存在，可恢复本轮现场；完整E2E固定试听夹具不可在已消耗名额的同库重复，复跑须新建隔离验收库，禁止清旧记录绕过规则。

```powershell
# 在 backend/ 构建；默认PATH可能是Java11，必须指定Java25
$env:JAVA_HOME='C:/Users/lxs/apps/jdk-25.0.4.1+1'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
& 'C:/Users/lxs/apps/apache-maven-3.9.16/bin/mvn.cmd' -B -s C:/Users/lxs/apps/maven-settings.xml -Pintegration verify
# 核实并停止此前自己启动的phase5-runtime.jar进程后复制新构建，勿杀全部Java
Copy-Item target/nurture-backend-1.0.0.jar target/phase5-runtime.jar
$env:NURTURE_DB_URL='jdbc:postgresql://localhost:5432/nurture_gap_acceptance_phase5_20261011'
$env:NURTURE_ADMIN_PHONE='13800000000'
$env:NURTURE_ADMIN_PASSWORD='Admin#2026'
$env:NURTURE_STORAGE_DIR='./target/phase5-files'
& "$env:JAVA_HOME/bin/java.exe" -jar target/phase5-runtime.jar --nurture.payment.sandbox.enabled=true
# 另一个终端 frontend/
npm.cmd start -- --host 127.0.0.1
npx.cmd ng build --configuration production
# 项目根目录（确认8080连接的是隔离库）
python tools/e2e_test.py
python tools/probe_authz.py
python deploy/verify_config.py
python tools/gap_fill_walkthrough.py
python tools/learning_plan_walkthrough.py
python tools/learning_materials_walkthrough.py
python tools/recurring_schedule_walkthrough.py
python tools/settlement_correction_rehearsal.py nurture_gap_acceptance_20261010_final
# 以下只在重新恢复的V10历史副本上执行；当前legacy已升级V12，勿直接重跑迁移演练
# python tools/legacy_migration_rehearsal.py <nurture_gap_acceptance_开头的V10副本名>
python tools/legacy_refund_rehearsal.py nurture_gap_acceptance_20261010_legacy
```

本机验收账号：学生13900000002/Student#2026、家长13900000003/Parent#2026、教师13900000001/Teacher#2026、运营13800000000/Admin#2026（只用于开发验收）。E2E先生成夹具再跑浏览器。注册限流生产规则50次/自然小时/IP不降低，测试按类/浏览器夹具隔离XFF桶。运行中的副本被Windows锁定时，只停止已确认CommandLine为phase5-runtime.jar（或确属本轮旧gap-runtime.jar）的本轮进程再复制，不按进程名全杀。

## 6. 新模型接手指南（按顺序执行）

1. 读本文件 → 2. 读 `项目交接.md` 的「当前进度」+「下一步」两节 → 3. 按需读 docs/ → 4. 核实：`ls backend/src/main/resources/db/migration/` 应为 V1–V17；`netstat` 看服务状态 → 5. **禁止**：重置/清理、改历史 Migration、动核心交易链路、引入禁用组件 → 6. 从第 7 节「下一步」第一项开始 → 7. 完成任务后回写交接文档（规则见 `AGENTS.md`）。

**历史陷阱速查**（完整版见交接文档末尾）：Docker 迁移 SQL 忌 min(uuid)；ArchUnit 须 1.5.1（Java 25）；匿名访问统一 403 非 401；越权探针写操作必须带幂等键；改迁移后必须重新 package；CSS `::after` 在产物中会被简写；e2e 的 notifications 返回键是 `items`；档期 DELETE=置 BLOCKED 非物理删。

## 7. 下一步开发计划（按优先级，2026-10-10 负责人补缺后更新）

退款红冲与三人对比已按原优先顺序完成；正式课/咨询/退款/受控材料等经用户“负责人逐步补齐”授权一并收口。既有阶段一、试听免费、改期安全与SUM修复保持已完成。下面只列接续工作，不将本轮未实现能力标绿。

| 优先 | 工作 | 首查文件与可执行下一步 | 验收 |
|---|---|---|---|
| 已完成准备 | 主库迁移副本与旧退款核对（实际主库订正未执行） | 读财务报告；数据库备份恢复、V11/V12副本迁移、消息守恒及38笔旧退款回滚对账已完成。阶段1配套备份与精确批次/回退方案已完成；实际业务库修改仍待明确数据范围（不阻断后续本地功能） | 消息数量守恒、历史金额逐笔匹配、可回退；未获数据执行范围前不修改主库旧账 |
| 已完成 | 阶段2每生每师免费试听限次与随机编号碰撞修复 | V13限次/并发行锁/只读资格UI，V14新编号序列，历史副本演练回滚；报告见docs | 40+79全过、E2E220/0、真实浏览器试听规则；历史记录不改 |
| 已完成 | 阶段3学习计划A | V15四表、预期版本行锁、原子发任务、三端UI与实时权限；报告见阶段3交付 | 40+92全过、E2E220/0、完整桌面/375px旅程 |
| 已完成 | 阶段4错题本B/附件C | V16错题/文件/容量，三端UI与受控下载，复用存储/任务；报告见阶段4交付 | 43+104、E2E220/0、三端完整旅程/字节验证 |
| 已完成 | 阶段5重复档期/ICS | V17有限材料化、冲突/例外/发布锁、规则分页和三端私有ICS；报告见阶段5 | 46+113、E2E220/0、6截图真浏览器；不批量扣款 |
| P1-1 | 阶段6运营分页/状态筛选、重新上架、客服证据、公开检索分页、首页示例治理 | 从admin dashboard队列读/翻页入手；teacher offering状态恢复；matching新增兼容分页契约；V18客服工单/私有证据，landing去无依据营销数字 | 超20无遗漏、归属/审计/幂等、销售检索一致、示例清晰 |
| P2 | 测验/阶段评估、外部提醒/PWA、课堂回放协作衔接 | ROADMAP D与执行清单；只计算真实学习证据，保持外部课堂方案 | 不虚构掌握度，不自研RTC/不接大模型 |
| ⛔ | 真实短信/支付退款提现、完整Staging | 用户明确当前没有外部资源；取得测试资源后按供应商适配与RUNBOOK实施 | 真投递/验签/重放/对账及全栈部署验收，不把静态预览当上线 |

完整状态与细化步骤见 `docs/补缺执行清单_2026-10-10.md`。Sites静态预览为前端交付，资源阻塞的全栈部署仍未完成。

## 8. 风险与限制

- V13保留历史重复活动试听，不能自动撤销旧预约；新预约受统一名额限制。计划开课时间已过的取消会消耗名额，界面已说明；旧CANCELLED不追罚。V14编号序列可有空号、超过六位时自然扩展，旧编号保留；不可多实例混跑旧随机编号生成版本与新序列版本。
- 阶段5本地46/113/220与真浏览器已通过；Sites仍私有静态v2，尚未包含本阶段新界面，阶段8统一更新；后端API仍未托管。

- 历史主库还在V10，没有应用新退款行为或订正旧账；配套备份/318上传文件恢复及38笔精确批次提交/重放/回退恢复已在隔离库通过18项断言。实际投入使用前仍须受控执行与重查，当前dry-run强制回滚不能用于直接修正。
- V12会合并同一师生重复会话并迁移消息，旧重复ID链接可能失效；已在恢复副本验证18条历史消息和合成重复组守恒、唯一约束及无孤儿；实际主库迁移仍未执行。
- 用户暂无短信服务商/支付商户/服务器/域名；没有真实投递、渠道退款或提现、没有完整Staging。Sites只为owner-private前端静态预览，顶栏说明API未连接。
- 退款仅全额、仅PENDING自动红冲；SETTLED/PAID_OUT及异常账目需人工核查。红冲fee=0保留原抽成快照，不用fee列总和推导退款后平台净收入。
- 学生前端默认家长付款，后端仍兼容旧学生payer订单；阶段1已明确保留原payer、不转移余额、不重复退款；主库58个学生payer订单均未付，无学生钱包余额。旧余额自助只读界面仍未补，家长下单仍需有效学生绑定。
- 聊天15秒轮询文本、没有附件/推送；运营列表仍首20/缺队列分页，公开检索数量有限；学习计划、错题本、教学资料/作业/答疑附件已完成；有限重复档期与ICS已完成；测验、消息附件与课堂协作仍待实施。
- 教师教龄为自报，公开资质只代表平台审核；旧账号缺资料需本人编辑。首页旧营销评价示例待治理。保留auth/landing/match三处CSS预算警告，production build无错误。
- 既有UUID校验仅解析而非严格v4/variant；ArchUnit只禁跨模块infra和api依赖infra，尚未覆盖全部架构文字承诺。未在本轮冒充已修复。
- 教学文件逻辑下架仍计200MB保留容量，目前没有自动物理清理/病毒扫描；聊天附件仍阶段7范围。
- 重复规则仅有限档期生成、例外在发布前设置，不是无限滚动排课或外部日历同步。
- 本地Git无远端，阶段4在ba2ddcd、阶段3在929ca88保护、核心补缺已在b9c7ae4保护，后续阶段提交以git log为准；文件系统及现有历史需保护，禁止reset/clean。`tools/settlement_walkthrough.png`接手前已存在。旧报告与表格按历史范围阅读，以最新代码与本节快照为准。
