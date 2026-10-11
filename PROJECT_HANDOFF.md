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
| `docs/交付报告_三项续作总验收_2026-10-11.md` | 最新三项验收/运行现场/完整测试/剩余限制 | 当前事实优先 |
| `docs/FILE_RETENTION.md` | 文件容量/引用保留/离线隔离与恢复命令 | 存储/清理前读 |
| `docs/交付报告_八阶段总验收_2026-10-11.md` | 八阶段最新交付范围、真实验证与剩余缺口 | 查看最终结果/交接时 |
| `docs/交付报告_无UI功能清单_2026-10-11.md` | 后端已上线但网页无入口的功能全清单（A~G分类+补UI优先级） | 做 UI 补齐/美化排期时 |
| `docs/FEATURE_ROADMAP.md` | 功能增补路线图（缺口复核 / A-F 新功能评估 / 四阶段顺序） | 规划下一批功能时 |
| `README.md`（功能根目录） | 构建与启动命令速查 | 启动环境时 |

## 3. 当前开发进度总表（2026-10-11 性能与持续负载轮最终验收）

状态图例：✅已完成且已验证 · 🟡已实现未充分验证 · 🔶部分实现 · 🔵规划中 · ⛔阻塞

2026-10-10 用户明确授权担任负责人并逐步补缺；历史“四问待批准”不再阻断新增代码/隔离测试。用户确认没有短信服务商、支付商户、测试服务器和域名。历史业务数据没有自动订正。

| 本轮补缺 | 状态 | 新验证证据 |
|---|---|---|
| 性能轮：10万条目/持续并发/冷启动验证与瓶颈修复 | ✅（P95延迟门槛如实未达，原因见交付报告 §6） | 隔离库100,000在售条目+5,000教师/2万评价/3千工单/1.5万消息真实归属元数据；8/16/32并发吞吐13.5→19.6→32.8rps线性、15分钟持续28,463请求零失败、JVM/连接池稳定、冷启动应用18秒+首访/预热差异记录；修复全部有执行计划证据：检索两阶段重写、verification去重（98,000次逐行探测→1次）、V22四列trgm索引、页序缺陷（负载中内容校验抓出2.2%失败→修复）、review_count NULLS FIRST（回归抓出→修复）、课程选择/工单列表N+1批量化；完整verify **49单元/架构+162IT 零失败**（16:05:40）、新验收库E2E **220/0**、探针**19拒绝/0漏洞**；未放宽门槛 |
| V11 全额退款红冲与并发防重 | ✅ | RefundReversalIT 12 例，取消/仲裁同事务、正向保留、非PENDING阻断、并发只退一次 |
| 历史订正只读审计与隔离演练 | ✅脚本 / 🔶业务库未执行 | dry-run强制回滚、重复跳过、钱包对账；主库仍V10 |
| 正式课预约 UI、真实公开教师详情、最多3人对比 | ✅ | PublicTeacherDiscoveryIT 5例 + 桌面/375px真浏览器 |
| 教师注册资料原子保存、资料编辑、DTO修复懒加载500 | ✅ | 注册断言 + 编辑后公开详情真实更新 |
| 咨询、未读/已读、最新100/更早分页、退款申请与进度 | ✅ | ConsultationAndMaterialIT 5例 + 双向咨询与家长订单退款浏览器走查 |
| 运营材料受控下载与审计、案例ID契约及两阶段退款UI | ✅ | 下载二进制比对、BOLA测试、运营实际预览/执行 |
| 短信不可用如实503/密码默认、家长付款旅程引导 | ✅ | SmsDeliveryFailureTest 3例、能力端点IT、相关订单归属与UI |
| 阶段6运营与检索/客服/重新上架 | ✅ | 46+129/E2E220/0、7真实浏览器截图、V18回滚48表不变；阶段6报告 |
| 阶段7测评/报告/咨询附件/通知 | ✅ | 46+143/E2E220/0/6图/V19/V20回滚52表旧列；见阶段7报告 |
| 阶段8工程契约/真实架构/旧钱包只读/样式预算/总验收 | ✅ | 49+147/E2E220/0/探针19拒绝0漏洞/52图/零CSS警告；Sites已更新，见阶段8与总验收报告 |
| 续作1公开检索数据库投影/性能 | ✅ | V21/固定两查询/2k10k20k五排序/8并发，49+162全量与E2E220/0收口，见续作1/总报告 |
| 续作2客服关联课程 | ✅ | 本人/ACTIVE监护范围/撤销保护，21课程分页/三端真实保存/运营详情/4图，全量收口 |
| 续作3文件容量/保留与离线隔离演练 | ✅限定本地范围 | 三类真实容量/3图、753引用SHA256守恒/1隔离1原样恢复/幂等/实际junction拒绝；无生产清理或永久删除 |
| 完整竞品能力、真实短信/支付、全栈部署 | 🔶 / ⛔ | 见 docs/补缺执行清单_2026-10-10.md，未完成项保持未完成 |

**八阶段执行进度（2026-10-11）**：阶段1–8均完成本地验证。源码V1–V20，最终49单元/架构+147IT、E2E220/0、探针19拒绝/0漏洞、生产构建零warning、52桌面/375px截图与原1,065条品牌CSS声明保留；Sites私有静态前端已更新。主库仍V10/资金未动，外部资源、全栈Staging与其它产品缺口保持未完成；见总验收报告。

以下既有轮次表保留原验证范围；当轮历史回归基线为单元38+ArchUnit2（合计40）、IT65、E2E218/0、探针19项拒绝/0漏洞、配置门禁全过、production build通过。正式课、聊天、退款UI、材料查看及Mock默认问题已补齐，早期竞品静态评估不再代表当前状态。

### 版本保护（2026-10-09 新增）
| 项 | 状态 | 说明 |
|---|---|---|
| 本地 Git 仓库 | ✅ | 基线 `9631f8d`（480 文件）+ G3 `7979522` + G7 `3f6da7d`；identity 本地署名，**无远程仓库**（恢复能力=commit 历史本身） |
| .gitignore | ✅ | 排除 node_modules/dist/target/日志/deploy 备份与部署包/.env（.env.example 反排除保留）/运行期上传文件 backend/data/files/（历史已跟踪文件不受影响）/__pycache__ |
| 凭据核查 | ✅ | application.yml 密钥全部 `${VAR:dev默认}` 注入，仓库内仅开发占位值；无硬编码真实凭据 |
| Sites 前端预览 | ✅ | 2026-10-11最新私有静态版本已更新（部署succeeded）：`https://nurture-platform-preview.kelly-cooper-4963.chatgpt.site`；Angular 前端静态包，构建提交 `537ac59f2aba9d31b885f6b4d02c43cf69bd7014`。后端 API 未部署，页面已显式标注受影响功能 |

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
| 六层测试体系 | ✅ | 最新44单元+5架构 / IT147 / E2E220 / 探针19拒绝0漏洞 / 配置全过与零warning生产构建（2026-10-11） |
| 备份恢复 | ✅ 实弹演练 | RTO 实测 55 秒（deploy/backup.sh + restore.sh） |
| 健康检查 | ✅ | actuator + probes + compose healthcheck + cloudflared 依赖 |
| 答辩 PPT / 计划书 / 小白指南 | ✅ | E:\线上家教平台\ 下三份 docx + pptx |

### 未完成 / 待决策（⛔/🔵）
⛔ Staging 部署（等：VM、域名、Cloudflare 账号——全部外部资源）
🔵 支付上线模式决策（A 沙箱 / B 线下收款【已实现】/ C 真实渠道【需商户资质】）
🔵 课堂形态决策（外部会议【当前】/ RTC SDK）
🔵 教育合规（ICP / 协议 / 未成年人条款）——正式运营最大前置
🔵 生产备份调度、AI 上层能力（均规划中）

## 4. 当前工作现场（2026-10-11 性能与持续负载轮最终快照）

性能轮完成并验证；源码**V22**（新增 V22__public_catalog_keyword_indexes.sql），16:05:40 完整 verify **49单元/架构+162IT 零失败**（loadperf-final-verify2.log；首跑 1 例 review_count NULLS FIRST 失败→修复→重跑全绿，失败日志保留）；新验收库 E2E **220/0**（loadperf-e2e.log）、权限探针 **19拒绝/0漏洞**（loadperf-authz.log）。报告 docs/交付报告_性能与持续负载_2026-10-11.md 与方案 docs/性能验证方案与门槛_2026-10-11.md；性能数据 backend/target/load-perf-results.json（25 次运行）、catalog-explain-after-v22.log、loadperf-e2e-server.log。全部失败日志保留。

**数据与运行**：隔离库 nurture_gap_acceptance_load_perf_20261011（V22，100,000 在售条目/5,000 教师/19,995 评价/24,997 档期/3,000 工单/15,129 消息/600 学习附件元数据，全部合成，种子 tools/load_perf_seed.py）；E2E 验收库 nurture_gap_acceptance_loadperf_e2e_20261011（V22，夹具已消费）。上传目录 backend/target/load-perf-files-20261011、loadperf-e2e-files-20261011。8081 回环性能服务运行中（load-perf-runtime.jar，最终代码 SHA256 C78238FC6E9623BE870E9A6C812F2B2C676934E882302EA374989F5529D89EED）；8082 E2E 实例（e2e-acceptance-runtime.jar）用后即止。**8080/4200 被用户自行启动的实例占用（nurture-backend-fat-1011.jar + ng serve），本轮未停止未触碰**；构建曾以其文件锁改用临时 finalName（pom 修改即还原）。

**⚠️ 主库状态差异（本轮实测记录，非本轮操作）**：主库 nurture 的 Flyway 版本在本会话开始时实测仍为 **V10**（14:xx 只读复核），本轮结束前实测已为 **V21**——为用户并行启动的 `nurture-backend-fat-1011.jar`（连主库、Flyway 自动迁移，15:36 后）所致；本轮所有实例仅连接隔离库，未对主库执行任何写操作。资金数据完好：**3 家庭钱包合计 3,640,000 分、150 订单不变**（16:5x 只读复核）。按「以代码为准，先记录差异」规则如实记录；主库历史订正的受控执行范围仍待用户明确。

**结论与限制**：P95 延迟门槛未在本机达成——外部 msedgewebview2 负载 19%~85% 波动 + 电池降频使同代码墙钟波动 3-10 倍（个体查询最好窗口 ≤1.1s、池稳定、零失败、吞吐线性）；不放宽门槛、不宣称生产 SLA/加速倍数。持续负载/病毒扫描/生产清理/外部资源能力各自仍未完成，不合并表述。

### 以下为历史现场快照（仅历史参考）

三项续作快照：源码V1–V21，13:04:17完整verify 49+162、E2E220/0、探针19拒绝/0漏洞；8080 PID18844/continuation-final-runtime.jar 连 nurture_gap_acceptance_continuation_final_20261011 V21；完整E2E夹具已消费，重跑须新命名隔离库。

**保留副本**：nurture_gap_acceptance_file_retention_20261011_130059 / target/file-retention-rehearsal-20261011_130059；批次2bb3a9d6-353e-4b42-986d-b909a489189e，plan/state/审计/恢复文件留存。7天无引用文件才可隔离，30天复核窗口，无永久删除/自动清理；引用包含逻辑移除与所有认证版本，配额不减。实际junction检查只移除已验证链接入口，外部哨兵保持。

**保护**：主库只读复核仍V10/3钱包合计3640000分/150订单不变，日志continuation-main-readonly.log；无业务库新迁移/历史订正。续作1 db24c6f、续作2 b2acdf9，最终检查点以git log为准；原tools/settlement_walkthrough.png SHA256 8976A50CCB0A3A0DF2E4C0E669FED5FED8CE24C4AD9E47023A18B621D382CD98不变/未提交。

**Sites**：13:01:30同一owner私有静态项目部署succeeded，源码537ac59f2aba9d31b885f6b4d02c43cf69bd7014、部署appgdep_6acb182331488191a541934e733357b5，URL https://nurture-platform-preview.kelly-cooper-4963.chatgpt.site；未连接后端提示保留。后端未部署/外部资源未补，不能当作全栈上线。

### 续作验证差异已解决


续作3全量回归发现代码/历史验证描述差异：PaymentApplicationService.confirmOfflinePayment 对已PAID订单明确返回idempotent=true，OfflinePaymentConcurrencyIT却把这种合法重放累计为首次成功，期望6并发中5次失败。新的测试池调度暴露该不一致。按代码契约区分首次状态转换与幂等重放，保留支付/结算/通知/审计严格一次断言，不改资金代码。第一次全量另因上下文连接池耗尽失败，仅测试profile限制6连接/1闲置并关闭性能服务；失败日志保留；最终已通过49+162全量，且新增付款Outbox一次断言。不改资金实现。

### 以下为历史现场快照（仅历史参考）

续作2专项完成；当时8080 PID12156/support-course-runtime.jar连接nurture_gap_acceptance_support_course_20261011(V21)，target/support-course-files，JAR SHA256 7B6A0ACE7616DC827BA23BD5BB05E1071DE7738EE783A5799BAB4AA8235AD78F。49+15IT、生产构建、4图通过；4200 PID18036/8081性能服务PID20544仍运行。下方续作1与phase8是历史现场，旧库/JAR/文件保持。下一步续作3后全量验收/最终隔离环境切换/Sites更新。报告docs/交付报告_续作2客服课程选择_2026-10-11.md。

三项续作获用户授权；第一项检索投影已专项验证，源码V1–V21。49单元/架构+13专项IT通过，44k全库/20k匹配条目五排序本机P95≤101.45ms、8并发最大146.20ms（人工夹具/热缓存，不是生产SLA）。8080仍下方phase8/V20现场；新增8081 PID20544/catalog-runtime.jar，仅127.0.0.1，连接nurture_gap_acceptance_catalog_perf_20261011(V21)，target/catalog-perf-files。下一步第2项客服课程选择，第3项文件保留策略/演练后全量验收切换。报告docs/交付报告_续作1检索投影与性能_2026-10-11.md；日志catalog-query-verify-retry.log/catalog-query-benchmark-final.log。

阶段1–8本地计划已完成，最终报告为docs/交付报告_八阶段总验收_2026-10-11.md。原ArchUnit条件方向错误/admin直连仓库与跨domain依赖已修正，用应用DTO/标量端口及五项严格规则/反例守门，不使用冻结豁免；原交易行为/事务/锁经过完整回归。阶段8检查点以git log为准（阶段7为096e474），无远端。

- **代码与迁移**：V1–V20，无本阶段新迁移；主库nurture仍V10；8080仅连接nurture_gap_acceptance_phase8_20261011(V20)，IT库V20；旧副本/JAR/文件保持。
- **测试**：08:55:30完整verify49单元/架构+147IT零失败；09:00:18最后架构复查49/0；E2E220/0、探针19拒绝0漏洞、配置全过、Angular生产构建零warning且原预算不变；8浏览器脚本52截图无溢出，CSS1,065声明守恒。日志phase8-final-verify.log、phase8-final-arch-unit.log、phase8-e2e/probe/config.log、phase8-*-browser*.log、phase8-css-preservation-final.log、frontend/phase8-final-release-build.log。
- **运行**：Docker deploy-postgres-1 healthy；8080 PID8804/phase8-runtime.jar，4200 PID18036，5432 PID36760；8081新增性能隔离服务见本节首段。
- **产物**：phase8-runtime.jar SHA256 32B7435DB6A35DD707025AF859FEB56057274B7FE4FF96E8450E14C1636442D7；target/phase8-files；.walk/各阶段截图及engineering-final/；阶段8与总验收报告。
- **修复与失败记录**：旧钱包只读按既有parent_profile归属，不创建/转移；旧COMPLETED统计比较修复。咨询附件375px原生输入溢出7px已修复重验；旧钱包SQL测试夹具字段/归属、浏览器GBK编码已修正后完整通过，失败日志保留。原CSS值只因CRLF/LF字面比较不同，规范化后全部一致；Sites输出目录改为支持的out。
- **历史资金**：未对主库迁移或订正。已备份恢复nurture_gap_acceptance_20261010_legacy并完成V11/V12真实Flyway迁移；17会话/18消息/90结算保持。副本重查A类38条/¥6545，事务订正对账通过且ROLLBACK恢复全部资金指纹。主库仍V10，不能把副本演练视为实际订正；下一步制定受控执行/回退范围，不去掉脚本保护直接执行。
- **版本保护**：核心补缺已提交本地检查点 `b9c7ae42cf30a8fdb7bc9489dd7e5693545d55d6`（88文件），无远端；最新阶段提交以git log为准。接手前已有 `tools/settlement_walkthrough.png` 未跟踪，保持原样。禁止reset/clean。AGENTS迁移核实已更新V20、本地Git无远端；旧报告继续保留历史范围。
- **阶段1收尾**：备份deploy/backups/stage1_20261010；stage1订正隔离库V12已提交38笔负向条目，回退副本nurture_gap_acceptance_stage1_recovery_20261010保持V10并恢复318文件。主库指纹不变；阶段1当时8080连旧final验收库，当前已切phase8，阶段1的8081已停止。
- **交付报告**：`docs/交付报告_核心旅程补缺_2026-10-10.md`、`交付报告_退款红冲与账务安全_2026-10-10.md`、`交付报告_验收与交接_2026-10-10.md`，执行清单 `docs/补缺执行清单_2026-10-10.md`。
- **Sites**：最新owner-private静态部署succeeded，原URL保持；source commit `dd22e5ccb04a7e5b3f8cd1e6b24d6b402a921301`，version `appgprj_6aca1413ee208191b584850869b65459~appgver_b0b12a2e8eac81918d1aab0ca12b21f0`，deployment `appgdep_6acb0eb66a9c81918ca30499ed7bc4b3`。不连接Java/PostgreSQL，顶栏说明接口操作不可用，不等同完整Staging；Sites源产物out，旧dist保留。


## 5. 当前运行与验证方式（PowerShell，本轮实测）

**性能轮命令（2026-10-11 实测通过）**：

```powershell
# 灌数（幂等守卫，只进隔离库）
python tools/load_perf_seed.py foundation ; python tools/load_perf_seed.py teachers 1250 # ×2 ; teachers 2500 ; business ; messaging ; counts
# 压测/校验（8081 回环实例）
python tools/load_perf_run.py validate --port 8081
python tools/load_perf_run.py scalecurve --port 8081
python tools/load_perf_run.py mixed --port 8081 --workers 8|16|32 --seconds 90 --label L
python tools/load_perf_run.py mixed --port 8081 --workers 32 --seconds 900 --label sustain-32
python tools/load_perf_run.py coldstart --port 8081   # 需先重启 PG 容器+应用
# E2E/探针指向非 8080 实例（本轮因用户实例占用 8080 而指向 8082 验收库）
$env:NURTURE_E2E_BASE='http://127.0.0.1:8082/api/v1' ; python tools/e2e_test.py ; python tools/probe_authz.py
# 构建：运行 jar 被外部进程锁定时用临时 finalName（改 pom → package → git checkout pom.xml）
```


先确认Docker Desktop与数据库容器健康、验收库已创建。禁止将测试命令指向历史主库；下面 phase8 库已存在，可恢复本轮现场；完整E2E固定试听夹具不可在已消耗名额的同库重复，复跑须新建隔离验收库，禁止清旧记录绕过规则。

```powershell
# 在 backend/ 构建；默认PATH可能是Java11，必须指定Java25
$env:JAVA_HOME='C:/Users/lxs/apps/jdk-25.0.4.1+1'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
& 'C:/Users/lxs/apps/apache-maven-3.9.16/bin/mvn.cmd' -B -s C:/Users/lxs/apps/maven-settings.xml -Pintegration verify
# 核实并停止此前自己启动的phase8-runtime.jar进程后复制新构建，勿杀全部Java
Copy-Item target/nurture-backend-1.0.0.jar target/phase8-runtime.jar
$env:NURTURE_DB_URL='jdbc:postgresql://localhost:5432/nurture_gap_acceptance_phase8_20261011'
$env:NURTURE_ADMIN_PHONE='13800000000'
$env:NURTURE_ADMIN_PASSWORD='Admin#2026'
$env:NURTURE_STORAGE_DIR='./target/phase8-files'
& "$env:JAVA_HOME/bin/java.exe" -jar target/phase8-runtime.jar --nurture.payment.sandbox.enabled=true
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
python tools/operations_support_walkthrough.py
python tools/admin_queue_walkthrough.py
python tools/assessments_messages_walkthrough.py
python tools/engineering_acceptance_walkthrough.py
node tools/verify_brand_css.cjs
python tools/settlement_correction_rehearsal.py nurture_gap_acceptance_20261010_final
# 以下只在重新恢复的V10历史副本上执行；当前legacy已升级V12，勿直接重跑迁移演练
# python tools/legacy_migration_rehearsal.py <nurture_gap_acceptance_开头的V10副本名>
python tools/legacy_refund_rehearsal.py nurture_gap_acceptance_20261010_legacy
```

本机验收账号：学生13900000002/Student#2026、家长13900000003/Parent#2026、教师13900000001/Teacher#2026、运营13800000000/Admin#2026（只用于开发验收）。E2E先生成夹具再跑浏览器。注册限流生产规则50次/自然小时/IP不降低，测试按类/浏览器夹具隔离XFF桶。运行中的副本被Windows锁定时，只停止已确认CommandLine为phase8-runtime.jar（或确属本轮旧gap-runtime.jar）的本轮进程再复制，不按进程名全杀。

## 6. 新模型接手指南（按顺序执行）

1. 读本文件 → 2. 读 `项目交接.md` 的「当前进度」+「下一步」两节 → 3. 按需读 docs/ → 4. 核实：`ls backend/src/main/resources/db/migration/` 应为 V1–V21；`netstat` 看服务状态 → 5. **禁止**：重置/清理、改历史 Migration、动核心交易链路、引入禁用组件 → 6. 从第 7 节「下一步」第一项开始 → 7. 完成任务后回写交接文档（规则见 `AGENTS.md`）。

**历史陷阱速查**（完整版见交接文档末尾）：Docker 迁移 SQL 忌 min(uuid)；ArchUnit 须 1.5.1（Java 25）；匿名访问统一 403 非 401；越权探针写操作必须带幂等键；改迁移后必须重新 package；CSS `::after` 在产物中会被简写；e2e 的 notifications 返回键是 `items`；档期 DELETE=置 BLOCKED 非物理删。

## 7. 下一步开发计划（按优先级，2026-10-10 负责人补缺后更新）

退款红冲与三人对比已按原优先顺序完成；正式课/咨询/退款/受控材料等经用户“负责人逐步补齐”授权一并收口。既有阶段一、试听免费、改期安全与SUM修复保持已完成。下面只列接续工作，不将本轮未实现能力标绿。

| 优先 | 工作 | 首查文件与可执行下一步 | 验收 |
|---|---|---|---|
| P0（新，2026-10-11 调研产出） | 补齐「有后端无前端入口」功能：先交易闭环（开始/完成课程、取消预约、我的预约列表）再公开评价列表等 | 读 `docs/交付报告_无UI功能清单_2026-10-11.md`（15项缺口+6项勿补的重复入口+7项死常量，已四角色浏览器核实） | 各角色浏览器走查按钮可达；不降低校验、不虚构数据 |
| 已完成准备 | 主库迁移副本与旧退款核对（实际主库订正未执行） | 读财务报告；数据库备份恢复、V11/V12副本迁移、消息守恒及38笔旧退款回滚对账已完成。阶段1配套备份与精确批次/回退方案已完成；实际业务库修改仍待明确数据范围（不阻断后续本地功能） | 消息数量守恒、历史金额逐笔匹配、可回退；未获数据执行范围前不修改主库旧账 |
| 已完成 | 阶段2每生每师免费试听限次与随机编号碰撞修复 | V13限次/并发行锁/只读资格UI，V14新编号序列，历史副本演练回滚；报告见docs | 40+79全过、E2E220/0、真实浏览器试听规则；历史记录不改 |
| 已完成 | 阶段3学习计划A | V15四表、预期版本行锁、原子发任务、三端UI与实时权限；报告见阶段3交付 | 40+92全过、E2E220/0、完整桌面/375px旅程 |
| 已完成 | 阶段4错题本B/附件C | V16错题/文件/容量，三端UI与受控下载，复用存储/任务；报告见阶段4交付 | 43+104、E2E220/0、三端完整旅程/字节验证 |
| 已完成 | 阶段5重复档期/ICS | V17有限材料化、冲突/例外/发布锁、规则分页和三端私有ICS；报告见阶段5 | 46+113、E2E220/0、6截图真浏览器；不批量扣款 |
| 已完成 | 阶段6运营/检索/重新上架/客服/示例 | V18四表，五队列分页、全量公开排序分页与私有工单证据；见阶段6报告 | 46+129、E2E220/0、7截图/V18回滚48表 |
| 已完成 | 阶段7小测/报告/咨询附件/通知 | V19/V20、有限客观题/教师批注/成绩独立分页/真实报告、私有消息附件与通知已读 | 46+143、E2E220/0、6截图、52表旧列回滚 |
| 已完成 | 阶段8工程契约与最终交付 | 严格UUIDv4、五项完整架构/应用端口、旧钱包只读、真实完成课统计、原品牌预算零警告、最终全量回归/Sites静态更新 | 49+147/220/52图；报告与现场见§4 |
| 已完成 | 续作1公开检索投影/本机性能 | V21/只读app端口/两SQL；见续作1与总报告 | 49+162全量/2k10k20k/8并发，本机非生产SLA |
| 已完成 | 续作2客服关联课程选择 | 本人/ACTIVE监护分页选择、真实保存与运营显示、撤销隐藏详情 | 49+15专项IT/构建/4真实图 |
| 已完成限定范围 | 续作3容量/保留/清理演练 | FILE_RETENTION.md；三类容量/引用保留/7天孤立文件隔离30天复核窗口，实际副本演练 | 753引用守恒/1隔离1原样恢复/实际junction拒绝/审计/幂等；49+162全量 |
| 已完成（2026-10-11 性能轮） | 更大数据规模、持续并发、冷启动验证与检索/列表瓶颈修复 | 方案与门槛先固定于 docs/性能验证方案与门槛_2026-10-11.md；V22 + 查询重写 + 批量化；结果与未达门槛的如实记录见 docs/交付报告_性能与持续负载_2026-10-11.md | 10万条目/32并发零失败15分钟；verify 49+162、E2E 220/0、探针 19/0；P95 门槛因本机外部负载未达，复测须干净时间窗 |
| P2 | 文件扫描/PWA/外部提醒/回放衔接与生产级负载/SLA | 不在本次范围夸大完成；依据具体提供商/后续范围实施 | 不虚构掌握度、不自研RTC/不接大模型 |
| ⛔ | 真实短信/支付退款提现、完整Staging | 用户明确当前没有外部资源；取得测试资源后按供应商适配与RUNBOOK实施 | 真投递/验签/重放/对账及全栈部署验收，不把静态预览当上线 |

完整状态与细化步骤见 `docs/补缺执行清单_2026-10-10.md`。Sites静态预览为前端交付，资源阻塞的全栈部署仍未完成。

## 8. 风险与限制

- **性能轮（2026-10-11）**：V22 改变公开检索执行结构（两阶段/去重/四列trgm索引），语义经 validate 七次与 162 IT/E2E 全量收口；改排序 SQL 时须记得外层重申 ORDER BY、LEFT JOIN 聚合表须 coalesce（DESC 排序 NULLS FIRST 教训，均已修复）。本机同代码墙钟波动 3-10 倍（外部 UI 负载 + 电池降频），跨窗对比无效；P95 门槛未达成、无生产 SLA；科目两字筛选（pg_trgm 三字符下限）走全表扫描为保留契约的已知成本；深分页 OFFSET 固有成本保留；未引入缓存/搜索引擎。
- 用户/并行会话可能在 8080/4200 启动自己的实例（本轮现场出现 nurture-backend-fat-1011.jar 连**主库 nurture**）——接手时先核实 8080 归属与其连接的库，**不得对非隔离实例跑 E2E**；e2e_test.py/probe_authz.py 支持 NURTURE_E2E_BASE 覆盖。**该实例启动时已将主库 Flyway 自动迁移 V10→V21（钱包/订单数据不变）**——任何指向主库启动应用的请求都会触发迁移，接手者须知悉。
- V13保留历史重复活动试听，不能自动撤销旧预约；新预约受统一名额限制。计划开课时间已过的取消会消耗名额，界面已说明；旧CANCELLED不追罚。V14编号序列可有空号、超过六位时自然扩展，旧编号保留；不可多实例混跑旧随机编号生成版本与新序列版本。
- 阶段1–8与三项本地续作已验证；最新49+162/E2E220/0、新7图及原八阶段52图。Sites同一私有静态版已更新，后端API仍未托管，静态登录/API操作不可用。

- 历史主库还在V10，没有应用新退款行为或订正旧账；配套备份/318上传文件恢复及38笔精确批次提交/重放/回退恢复已在隔离库通过18项断言。实际投入使用前仍须受控执行与重查，当前dry-run强制回滚不能用于直接修正。
- V12会合并同一师生重复会话并迁移消息，旧重复ID链接可能失效；已在恢复副本验证18条历史消息和合成重复组守恒、唯一约束及无孤儿；实际主库迁移仍未执行。
- 用户暂无短信服务商/支付商户/服务器/域名；没有真实投递、渠道退款或提现、没有完整Staging。Sites只为owner-private前端静态预览，顶栏说明API未连接。
- 退款仅全额、仅PENDING自动红冲；SETTLED/PAID_OUT及异常账目需人工核查。红冲fee=0保留原抽成快照，不用fee列总和推导退款后平台净收入。
- 学生前端默认家长付款，后端仍兼容旧学生payer订单；阶段1已明确保留原payer、不转移余额、不重复退款；主库58个学生payer订单均未付，无学生钱包余额。旧余额自助只读界面已补并隔离验证，不自动创建钱包，家长下单仍需有效学生绑定。
- 聊天15秒轮询，私有咨询附件与通知分页/已读已完成，外部推送仍未实现；运营五队列/公开分页/重新上架/私有客服已完成；公开分页源码已改为数据库投影/两查询并做本机20k匹配条目验证，8080已切换为最终V21隔离版本；生产持续负载仍未验证，旧私有推荐20条接口保留；学习计划、错题本、教学资料/作业/答疑附件已完成；有限重复档期与ICS已完成；有限客观测验/教师批注/真实报告已完成，课堂协作/外部提醒仍待资源与后续范围。
- 教师教龄为自报，公开资质只代表平台审核；旧账号缺资料需本人编辑。首页/登录旧数字已清理、场景评价已明确非真实；auth/landing/match原品牌声明保留，production build零warning且预算未提高。
- UUID现在严格规范v4/variant；原ArchUnit方向错误已修复，五项全量严格检查包含坏/好反例，无冻结豁免。接口状态快照为字符串，改变状态名称须同步应用端口消费者。
- 教学文件逻辑下架仍计200MB，咨询与工单证据各100MB；三类容量反馈和引用保留已完成。仅离线命名副本无引用7天文件可恢复隔离/30天复核窗口，不提供生产永久清理、自动到期销毁或病毒扫描。
- 重复规则仅有限档期生成、例外在发布前设置，不是无限滚动排课或外部日历同步。
- 本地Git无远端，阶段4在ba2ddcd、阶段3在929ca88保护、核心补缺已在b9c7ae4保护，后续阶段提交以git log为准；文件系统及现有历史需保护，禁止reset/clean。`tools/settlement_walkthrough.png`接手前已存在。旧报告与表格按历史范围阅读，以最新代码与本节快照为准。
