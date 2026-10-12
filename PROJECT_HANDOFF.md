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

## 3. 当前开发进度总表（2026-10-11 方向 D 全站 UI 重构轮验收）

状态图例：✅已完成且已验证 · 🟡已实现未充分验证 · 🔶部分实现 · 🔵规划中 · ⛔阻塞

2026-10-10 用户明确授权担任负责人并逐步补缺；历史“四问待批准”不再阻断新增代码/隔离测试。用户确认没有短信服务商、支付商户、测试服务器和域名。历史业务数据没有自动订正。

| 本轮补缺 | 状态 | 新验证证据 |
|---|---|---|
| 四角色工作台园林背景优化「景观清晰，内容柔和」（学生/导师/家长/运营，remake__front 工作树未提交） | ✅ | 四个 dashboard css 纯增量（全 ≥1024/1280 媒体查询，≤767 零改动）：撤整层 opacity 0.45 与重白横幕→`filter: contrast(1.05) saturate(0.95)` 零 blur 真实材质、景观带 400px（运营 380）、mask 末端渐隐 180px 融入 --bg-page、::after 左→右分区蒙层 0.72/0.48/0.25（运营 0.78/0.54/0.30 更克制）；根因=泛白来自整层透明+重白横幕而非 blur，hero.avif 2560×1440 源图清晰无需生图；tools/garden_backdrop_2026-10-12.py **105/105**（含 375 四角色改前改后快照零差异）；ng build 零警告；品牌锁 PASS 1001@c336d71；视觉验收 6/6 pass；截图 docs/screenshots-2026-10-12-garden/ |
| 家长端整页桌面排版修复（8 模块全链：子女/绑定表单/钱包/对账表/课程订单/家庭订单表/进度保障/通知，remake__front 工作树未提交） | ✅ | 家长端两组件 css 纯增量（全部 ≥1024/1280 媒体查询，≤767 零改动）：模块 28px(上下)/32px(左右)、标题 22px/700、说明 15px/1.7 限宽 1060、徽标/药丸 14px、表格 15px+表头 15px/600+单元格 16×18+行高 ≥56+金额 17px+tabular-nums、家庭订单表列宽 30/22/20/28%、绑定输入框 48px、按钮 15px/40px、通知卡（共享组件 :host ::ng-deep 家长端作用域覆盖）20×24px/16px；tools/parent_typography_2026-10-12.py **154/154**（1700/1440/1280 断言+1024 中档+375 改前改后快照零差异）；ng build 零警告；品牌锁 PASS 998；视觉验收 5/5 pass；截图 docs/screenshots-2026-10-12-parent/ |
| 四角色工作台桌面间距精修（贴边/对齐线/节奏统一，remake__front 工作树未提交） | ✅ | 7 文件纯增量（5 css + 1 ts 内联 + 1 ts 内联改一字符）：裸 nur-card 主模块补内边距 ≥1280 左右 32/上下 24、1024–1279 档 24；学生 7 裸模块（bookings/notice/profile×2/schedule/quick-ask/legacy-wallet）+家长 4 模块（`section.nur-card` 子选择器）+admin 面板 24/32+指标卡 20px；头部节奏 card-desc 8px/card-head 20px；预约双列 gap 20/24、筛选栏-内容 20px；教师栅格 18→20；钱包/绑定/订单条 24 档；工具 spacing_polish_verify_2026-10-12.py **20/20**（四尺寸×四角色 padding/对齐共线 ±1.5px/badge 右距 ≥28/gap/零溢出）+ spacing_probe_mobile.py 改前后 375 基线**零差异**；ng build 零警告；品牌锁 PASS 998 条（并行会话新基线 43b823b，本轮零锁内文件）；视觉代理 8/8 pass；截图 spacing-*.png 8 张 |
| 四角色工作台桌面宽屏统一改造（学生/家长/导师/运营 + 学生三业务页 + 教师档期页，remake__front 工作树未提交） | ✅ | 10 文件纯前端增量（8 css + 2 ts 内联样式追加），全部在 `@media (min-width:1024/1280px)` 内、手机端零改动；桌面壳 1560px/calc(100%−80px) 居中、学生预约 ≥1280 双列 18/24、档案/画像表单双列、家长周报反馈双列+正文限宽 880、教师双列栅格 18/24+资料表单限宽 920、运营指标 6 列+队列面板满宽；Edge CDP 量测 46/46（1920/1700/1440/1280/1024×四角色壳宽=min(1560,cw−80)、390/375×8 路由 max-width 保持原值、全程零横向溢出）+ 学生展开态 7/7（预约 751px×2@≥1700）+ 交互冒烟（筛选 10→5/教师分区/运营配置）+ admin 三 Tab 走查；ng build 零警告；品牌锁 PASS 996 条（基线 87c3764，不涉锁文件）；工具 tools/desktop_layout_verify_2026-10-12.py + tools/student_expanded_verify_2026-10-12.py，截图 docs/screenshots-2026-10-12-desktop/（57 张） |
| 方向 D 全站 UI/UX 重构（用户选定黛绿暖金浅色方向，remake__front 分支） | ✅ | tokens.css 重写+旧令牌别名、首页/登录/头尾重写、五域子代理并行零冲突；`npx ng build` 零错误零警告；品牌锁基线 096e474→38682ba（618 条 PASS）；四角色+公共页真浏览器走查（截图 docs/ui-redesign-2026-10-11/images/）；提交 ed0c636/38682ba/4e6151a/140f7fa/98f7c41；设计契约 design-system-d.md |
| P0「有后端无 UI」交易闭环补齐（随重构轮落地） | ✅ | A1 预约两阶段取消（preview 实测退款影响 ¥300 预览，未点终态）、A2 课堂开始/完成按钮、A3 我的预约列表（121 条真数据+状态筛选+分页）；B1-B7/C1 公开评价（前端切片分页）/D1 平台配置 PUT 回显/D2 限流重置；7 死常量删除（grep 零引用）；家长代管取消因后端 403 如实不接假 UI |
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
| 本地 Git 仓库 | ✅ | 基线 `9631f8d`（480 文件）+ G3 `7979522` + G7 `3f6da7d`；identity 本地署名。**2026-10-11 已添加远程** `origin = https://github.com/xusheng-123/myjiajiao.git`，main 全历史已推送并设跟踪，远程 HEAD=`bd8f054` 与本地一致（推送账号 xusheng-123，URL 内嵌用户名区分本机缓存的另一账号 wohaoshuai-666） |
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

## 4. 当前工作现场（2026-10-12 首页 2–9 屏清晰度统一 + 四端园林背景 + 家长端整页排版 + 间距精修 + 第二屏定稿 + 四角色宽屏 + 页脚晨雾过渡多轮快照）

**最新轮（首页 2–9 屏园林背景清晰度统一，2026-10-12，已提交 6247479+锁基线 fc878f4）**：用户指出第 2–9 屏「模糊感、过度泛白」与第一屏摄影质感形成落差。**诊断**：6 处 backdrop-filter 毛玻璃（3/4 屏标题罩+链接 pill blur(6px) 在 landing.component.css、5/6/7 屏标题罩+第 6 屏步骤罩 blur(3px) 在 landing-sections-2.css）+ 桌面档整屏白面纱（中部 α 0.24–0.42、顶部最高 0.66）+ 第 8 屏 section-horizon 源图雾烤进图里（约七成白雾无细节；素材全 1672×941 vs hero 2560×1440）。**修复**（仅 landing 三 css + horizon 素材）：①撤全部 backdrop-filter，柔光罩改纯色羽化微升 α 补可读（0.5→0.58/0.42→0.52/pill 0.62→0.72）；②桌面档面纱内收非文字区 0.08–0.20、屏间过渡 ≤0.30（≤767 移动档与 cta::after 页脚渐隐原值不动）；③第 8 屏生图重生成（gpt-image-2.5 保构图/光线/色调、晨雾只留山脚，1672×941 avif 314KB/webp 379KB）；④vision 居中文字补局部羽化柔光罩（0.72 径向 mask）+ overflow-x clip。**验收**：tools/garden_clarity_2026-10-12.py 全绿（桌面 10 屏+移动 5 屏；secTop/secH 改前改后逐字段相等=结构零改动；6 处 backdrop-filter==none；面纱峰值 ≤0.30；hero 感知级一致 corr=1.0/mean 0.705/over32 0.26%）；ng build 零警告；品牌锁 argv PASS 1006 条（1001 −4 backdrop +9 vision 罩）→ 基线 6247479 无参 PASS；视觉验收代理 19/19 pass；截图 docs/ui-redesign-2026-10-11/images/home-clarity/（comparison-before-after-1700.png 总览）。**新坑**：①绝对定位柔光层 inset 负值窄档横向溢出（375 scrollW=447），柔光/羽化层须随兄弟屏加 overflow-x: clip；②Edge 跨会话确定性光栅化抖动（同状态两轮逐字节相同、跨会话 AA 细差 mean 0.7/255），「零改动」断言须用 shift0 相关+均值差+强差异占比判据而非字节相等。遗留候选：corridor/study 半侧粉墙属自然构图未重生成；其余 7 素材仍 1672×941。

**最新轮（四角色工作台园林背景优化「景观清晰，内容柔和」，2026-10-12，工作树未提交待用户指令）**：用户指出四端背景「过度模糊、过度泛白、光影层次不足」，要求恢复官网同款超写实雨后苏州园林质感；任务书给出参数（blur 0–2 优先 0/contrast 1.05/saturate 0.95/brightness 1.0、顶部景观 380–420px、底部渐隐 140–180px、分区蒙层 0.72→0.48@48%→0.25、下方融入 #F4F5F0），并要求先排查模糊真实来源、优先修 CSS、素材确需重制才生图。**根因排查结论：零 blur/backdrop-filter——泛白与层次丢失来自 `.dash-backdrop` 整层 `opacity:0.45` + `::after` 重白横幕（0.9/0.4/0.8）+ 300px 全程渐隐 mask；背景图 hero.avif 为 2560×1440 高清源图（首页同款），素材无需重制**。修复仅动四个 dashboard 组件 css（学生/导师/家长同款块，运营端变体；全部 ≥1024/≥1280 媒体查询，≤767 手机端零改动）：撤 opacity→1、加 `filter: contrast(1.05) saturate(0.95)`、`background-repeat:no-repeat`；景观带 400px（中档 360）；mask 改 `#000 0% → #000 55% → transparent 100%`（末端渐隐 180px，中档 52%/172px）；::after 改左→右分区蒙层 `rgba(247,248,243,0.72) 0% → 0.48 48% → 0.25 100%`（中档 0.76/0.50/0.28）；运营变体：380px/渐隐自 60%（152px 快过渡）/蒙层 0.78/0.54/0.30。渐隐融入色保持页面既有 `--bg-page: #F2F3EE` 令牌（任务书参考 #F4F5F0 近似；mask 渐隐到透明天然无缝，且禁改共享全局变量）。业务卡片/布局/文字零改动。验收：新工具 tools/garden_backdrop_2026-10-12.py **105/105**（1700/1440/1280×四角色：高度/opacity/滤镜/cover no-repeat/pointer-events none/mask 渐隐位置/蒙层三档断言 + 1024 中档 + 全档零溢出 + **375 四角色改前改后 computed 快照逐字段零差异**）；ng build 零警告；品牌锁 PASS 1001 条（新基线 c336d71，并行会话本轮期间落地第二屏参考图定稿两提交，与本轮四 dashboard 文件零交集）；视觉验收代理 6/6 pass（月洞门拱券/灰瓦檐角/粉墙爬藤/竹柳与暖光灯笼可辨、四端同体系、运营更克制、渐隐无硬切线；截图 docs/screenshots-2026-10-12-garden/）。**新坑一枚：Chrome computed `mask-image` 把 `#000`/`transparent` 序列化为 `rgb(0,0,0)`/`rgba(0,0,0,0)`，断言按序列化值匹配**。遗留候选（非本轮范围）：学生端「已确认」圆章文字垂直偏上（既有徽章内边距问题，视觉代理 1700/1024 两档发现）。

**上一轮（第二屏「办学理念」参考图定稿，2026-10-12，已提交 c336d71+49ad9af）**：用户以参考图下达第二轮文字定位（并反馈「上次修改后文字位置实际上没有变化」——实测上一轮 24.7% 方案已生效（改前探针 kickX=412@1700），系参考图目标更靠右且新增理念居中对称要求）。落地仅 landing-sections.css 一个 `@media (min-width:1280px)` 块（landing.component.css 回退上轮基础改动 620/去 1100 上限）：①主文字 padding-left 比例式 `max(calc(560px − 14.1vw), 24px)` → 左缘 ≈35.9% 视口宽（1700 实测 X=603，参考图 600/1672；月洞门恒占 ~23vw 等比移动故必须比例式）；②三组理念 `width:810px + margin-left:-124px` → 行心与主标题字心重合（偏差 2.6px，标题字宽须 Range.selectNodeContents 实测 561.5px——h2 块级 rect 宽=内容盒宽 966px 的坑），三等宽列左右对称（列心距差 ≤1.5px）、组内 text-align:center；③正文 max-width 488px 锁参考图换行点（前 30 字实测 480.0px 整，首行止于「不催促，」）；原 .principles 顶部分隔线样式不动、随行移动。验收：tools/philosophy_reference_check.py 五档断言全过（1024/375 档 padL=24/620px/start 对齐结构性零改动，纵向五基线 ±14 锁死，Range 断言 30 字 1 矩形/31 字 2 矩形）；home_sections_walkthrough 回归零变化；ng build 零警告；品牌锁 argv→基线 43b823b→c336d71（1001 条）无参 PASS。截图 docs/ui-redesign-2026-10-11/images/home-sections/philosophy-ref2-{before-1700,1700,1440,375}.png。用户侧若仍见旧位置：Ctrl+F5 强刷并确认视口 CSS 宽 ≥1280（Windows 175% 缩放 1080p 屏=1097px，媒体查询不生效）。

**上一轮（家长端整页桌面排版修复，2026-10-12，工作树未提交待用户指令）**：用户验收间距精修轮后下发家长端专项任务书「整张页面从上到下，不是只修第一屏」，给出完整参数表（模块上下 28px/左右 32px、标题 22px/700、说明 15px、辅助 14px、表格 15px/表头 15px/600、状态标签 14px、金额 17px、按钮 15–16px/40px、单元格 16×18、行高 ≥54/56、操作按钮间距 10–12px），并明确禁止只加 margin-left、要修真正 CSS 原因。修复仅动家长端两个组件 css（全部 ≥1024/1280 媒体查询，≤767 结构性零改动）：①parent-dashboard.component.css——模块 padding 28px 32px（≥1280）/24px（中档）、`.card-title` 22px/700、`.card-desc` 15px+mt8、`.quiet-note` 15px/1.7、count/gold 徽标 14px、card-head mb20、`.dashboard-shell .nur-btn--sm` 15px/40px（.dashboard-shell 前缀压过全局 components.css）、说明限宽 880→**1060px**（任务书 §八 1000–1100）、**通知中心（四端共享组件）用 `:host ::ng-deep nur-notice-center …` 仅家长端作用域覆盖** toggle 20×24px/16px、list 16×24px；②parent-dashboard-forms.css——`.txn-table` 15px、th 15px/600、th/td padding 16px 18px+line-height 1.7（行高 ≈57px）、td `font-variant-numeric: tabular-nums`（数字对齐）、type/status 药丸 14px、memo/guide/paid-note/form-label/form-status/sandbox 14px、balance-label 15px、`.font-mono` 15px、`.amount-col` 17px+tabular、bind-title 16px、家庭订单表列宽 nth-child **30/22/20/28%**、bind-new-card gap 12px+form-row mt8（说明→表单 20px）+`.bind-input` min-height 48px。颜色令牌（#1E3A31/#2F3A36/#5C6662）与任务书参考色（#244638/#3E5047/#596960）不同但 §十一 禁改共享全局变量，保持原令牌（对比度达标）。验收：**tools/parent_typography_2026-10-12.py 154/154**（1700/1440/1280×：模块 28/32、标题 22/700、徽标 14px 右距 ≥28、左对齐基线 ±1.5、表格 15/600/16×18/行高、金额 17、列宽 ±2、绑定输入框 48、充值按钮 40/gap12、通知 toggle 20×24/16 + 1024 中档 24px/标题保持 20/600 + 全档零溢出 + **375 改前改后 computed 快照逐字段零差异**）；ng build 零警告；品牌锁 PASS 998 条（家长端 css 不在锁内）；视觉验收代理 5/5 pass（parent-full-{1700,1440,1280,1024}.png + parent-mobile-375.png，截图 docs/screenshots-2026-10-12-parent/）。minor 两条不阻塞：钱包流水表金额绿/订单表金额金为既有业务色语义（正负区分）未动；手机端「共 1 名子女」徽标 375 折行属既有状态（本轮 ≤767 零改动）。

**上一轮（四角色工作台桌面间距精修，2026-10-12，工作树未提交待用户指令）**：用户验收宽屏改造后指出「文字/标题/筛选按钮/统计距卡片边框太近」。根因=学生/家长端多数主模块是裸 `nur-card`（基类无 padding、组件也无规则），内容直接贴卡边框。修复（7 文件纯增量，全部 ≥1024 媒体查询）：①student-dashboard.component.css——7 个裸模块（bookings/notice-binding/student-profile/learning-profile/schedule/quick-ask/legacy-wallet）≥1280 `padding:24px 32px`、1024–1279 档 24px；`.card-desc` margin-top 8px、`.card-head` margin-bottom 20px；②student-dashboard-bookings.css——预约双列 gap 18/24→**20/24**、`.status-tab-group` margin-bottom 20px、`.booking-main-info` gap 8px、profile-cards-grid 20/24、中档 `.booking-item-card` 20px；③parent-dashboard.component.css——`.dashboard-shell > section.nur-card`（四大模块，子选择器防 forms.css 后加载压制）24/32 档 + card-head 节奏 + 中档 child-card 20px；④parent-dashboard-forms.css——bind-new/wallet-overview/order-select 内层卡 ≥1280 24px、中档 20px、`.topup-group` gap 12px；⑤teacher-dashboard.component.css——栅格 18/24→**20/24**；⑥admin——面板 24/32、`.metric-card` 20px、`.queues-container` gap 20px、shell gap 24px；⑦teacher-profile-editor——`.profile` ≥1024 `padding:24px 32px`（与其余模块对齐基线一致）。教师子卡片本就 32px 无需动。**同文件后加载覆盖坑**：styleUrls 第二文件（bookings/forms css）在后，同特异性覆盖必须写进后加载文件或提特异性，否则媒体查询规则被静默压制（本轮 student `.booking-main-info`、parent 内层卡即此处理）；中档规则必须 `(min-width:1024px) and (max-width:1279px)` 封顶，否则源序压过 ≥1280 档（家长 child-card 首跑 FAIL 即此因）。验收：tools/spacing_polish_verify_2026-10-12.py **20/20**（1920/1700/1440/1280/1024×四角色：模块 padding 断言、标题-筛选栏-首卡左缘共线 ±1.5px、count-badge 右距 ≥28、双列 gap 20/24、零横向溢出）；tools/spacing_probe_mobile.py 改前/改后两次 375×812 四角色全量 computed style 快照**零差异**（手机端零改动实证）；ng build 零警告；品牌锁 PASS **998 条（并行会话新基线 43b823b**，其第二屏右移提交落地前跑锁会 FAIL——竞态假阴性，落地后自愈）；视觉验收代理 8 张 spacing-*.png 全 pass。截图 docs/screenshots-2026-10-12-desktop/spacing-*.png。



**本轮（第二屏「办学理念」文字右移，43b823b+锁基线提交）**：用户规格书授权仅调桌面端文字水平位置（右移 ~100px 落中央粉墙避开月洞门）。核心几何：月洞门在背景图中恒约占视口宽 23%（cover 等比随视口移动），固定 padding 无法各档保净距 → 桌面档（≥1280）`.philosophy { padding-left: max(calc(560px − 25.3vw), 24px) }`（反解自「容器左缘+padding=24.7% 布局宽」：1700 实测 X=412（目标 420、较改前 307 右移 +105）、1440→348、1280→313，与门洞右缘恒 ~16px 净距；>2120px 回落 24px 兜底）；正文 max-width 620→650、三组理念容器 max-width 1100（landing.component.css）；第三组距右缘 628px。≤1279 与 ≤767 档零改动（min-width 结构性保证，探针 computed 与基线逐一相等）；标题 46.4px/1.5 单行、纵向五档基线（±14）锁死；背景/月洞门/文案/颜色零改动（第二屏无柔光伪元素零脱节）。验收：**新工具 tools/philosophy_shift_check.py** 五档断言全过 + 截图（philosophy-before-{1700,1440}/shift-{1700,1440,375}.png）；home_sections_walkthrough 8 屏回归零变化、375 scrollW=375；ng build 零警告。品牌锁 argv(43b823b) PASS 998 条（净增 2）→ 基线 87c3764→43b823b。**坑**：中档固定 padding 反而压门洞（1440 门洞右缘 ≈334，固定 130px 文字只到 X=284 叠上门洞）→ 必须比例式；Python 3.14 无 get_event_loop，CDP 工具须 asyncio.run。⚠️ 工作树仍有四角色宽屏轮未提交改动（含 PROJECT_HANDOFF/CHANGELOG 该轮回写），本轮文档提交用 git apply --cached 只择本轮 hunk。

**上一轮（四角色工作台桌面宽屏统一改造，2026-10-12，工作树未提交待用户指令）**：用户任务书授权「四角色电脑端统一 1560px 宽屏、手机端绝对不变」。落地 10 文件纯增量：student-dashboard.component.css+bookings.css（壳 1560/专注按钮 auto/档案画像表单双列/预约 ≥1280 双列 grid 18px24px/卡 padding 24px/正文限宽 880）、parent-dashboard.component.css（壳/周报反馈双列/子女卡 padding 24/表格列距加宽）、teacher-dashboard.component.css（壳+双列栅格 gap 18/24）、admin-dashboard.component.css（壳+指标 gap 20+面板 padding 28+tab gap）、student-growth/archive/questions.component.ts 内联样式（壳 820/820/980→桌面容器）、scheduling/recurring-availability.component.css（壳 1120→桌面容器）、teacher-profile-editor.component.ts（≥1024 表单限宽 920px）。全部规则仅存在于 ≥1024/1280 媒体查询；≤767 探针 computed max-width 与原值逐一相等（860/820/980/1120）结构性证明手机端零改动。验收：ng build 零警告；品牌锁 PASS 996 条（基线 87c3764，本轮不涉锁文件）；tools/desktop_layout_verify_2026-10-12.py 46/46（5 桌面尺寸×4 角色 shell=min(1560,clientWidth−80)、8 路由×2 移动尺寸 max-width 保持+零溢出）+ tools/student_expanded_verify_2026-10-12.py 7/7（学生专注模式展开后预约双列 751px×2@1920/1700、单列@≤1024）+ 交互冒烟（预约筛选 10→5、教师「档期与经营」分区切换、运营「平台配置」面板）+ admin 账户治理/线下收款 Tab 宽屏走查；截图 docs/screenshots-2026-10-12-desktop/（46 量测 + 7 学生展开 + 4 移动顶部）。**新坑三枚**：①学生工作台 focusMode=signal(true) 默认专注模式，预约列表等卡片须点「展开全部功能」才渲染（验收脚本须先展开）；②Edge CDP 首次导航前 localStorage 注入落在 about:blank 原点不生效→角色守卫弹回登录页，必须先导航一次 localhost 预热原点；③`width:calc(100%−80px)` 按 clientWidth 解析（headless 桌面滚动条 15px），壳宽断言须用 clientWidth 而非 innerWidth。⚠️ 并行页脚轮快照里「student dashboard 两个 css +111 行非本任务改动」即本轮早期工作树状态（实际为本轮 5 个 student 文件 +111 行），其提交 87c3764/eede016 已正确排除本轮文件；本轮 10 文件改动仍留在工作树未提交，待用户指令（提交建议拆 feat+验证工具两笔或合为一笔，遵用户习惯）。

**上一轮（页脚晨雾过渡 + 排版参数表，87c3764+锁基线提交）**：用户规格书授权页脚改版（「青石渐隐、竹影收尾、晨雾过渡」）：①第九屏→页脚硬分界消除——`.nur-section.cta::after` 渐隐层 200px（移动 ≤767 为 120px）`transparent→0.38→0.8→#F5F4ED` 把湿润青石/倒影融入页脚顶色，`pointer-events:none` 零版式影响；②页脚 `site-footer.component.css` 全量重设计——底色 radial 白雾 + `#F5F4ED→#EFF2E9 55%→#E7EEE8`，右上淡竹/左下苔石 `::before/::after` **直接裁切第九屏源图 section9-courtyard.png 复用免生图**（footer-bamboo/footer-stone webp，径向 mask 羽化，opacity 0.5/0.42，移动 0.26/0.22），内容 z-index:1 保文字干净；③排版参数表全落地：1200px/1.3:1:1.2/64-56px/品牌 24px#244638/栏目 17px/正文 14px 行距 20px#61766B/暖金 #B28B57/版权区 64px/分隔线 rgba(47,78,60,0.08)/移除 border-top；移动档单列 28px 组距；文案链接联络锚点零改动。验收：**新工具 tools/footer_transition_check.py** 三档 DOM 断言全过 + 交界截图三档目检无分界（footer-seam/footer-full-{1920,768,375}.png）；cta_center_check 与 home_sections_walkthrough 回归全过（第九屏与 2–8 屏/hero 零变化）；ng build 零警告。品牌锁 argv(87c3764) PASS（896 条）→ 基线 2d86955→87c3764，**files 纳入 site-footer.component.css（路径前缀改全路径）**→ 无参 PASS（996 条）。坑：`.sf-verse` 被 `.sf-bottom-inner p`(0,1,1) 压色（原文件即存在）→ `.sf-bottom-inner .sf-verse` 提权；768 命中 footer 自身 ≤768 移动块而 sections-2 移动块 ≤767，断言分档写。⚠️ 工作树有非本任务 student dashboard 两个 css 未提交改动（+111 行），保护勿动勿纳入。

**上一轮（第九屏撤景窗 + 居中排版，2d86955+锁基线提交）**：用户规格书授权两项：①移除右侧拱形景窗恢复自然粉墙——核实为独立前端元素 `figure.cta-frame`（cta-lattice 图）非背景图内容（section-courtyard 本无景窗，零改图），整块删除 figure + `.cta-frame` 三段 CSS + `git rm` cta-lattice.{avif,webp}；②居中排版全参数落地：900px 居中（.cta .cta-inner 压 .nur-container）、flex 居中 + min-height 760px + 底部多留 88px → 内容视觉中心 ≈45%（实测 0.442）、标题 44px/700/1.35、副标题 18px/1.7、间距 20/20/34、按钮 52px/16px/水平 30px；三档响应式 ≥1280（44/900）/768–1279（36/760）/≤767（clamp(28,7vw,34)+边距 20+按钮竖排）；文案/字体/颜色/业务功能零改动。背景取景适配：桌面 center 38%→48%（左门檐/中粉墙/右竹/下青石同框）、移动 55%→50%。验收：**新工具 tools/cta_center_check.py** 五档断言全过 + 三档截图目检（cta-center-{1920,1024,375}.png）；walkthrough 复测 2–8 屏几何与 hero 零变化；ng build 零警告。品牌锁 argv(2d86955) PASS（886 条，净增 10）→ 基线 c6e9213→2d86955。**坑**：桌面 scrollW=vw−15 为滚动条宽度，溢出断言写 `scrollW>vw`；仿真宽恰 1280 落桌面档属正确，平板档测 1024。

**上一轮（5–9 屏生图 + 接入，c6e9213+锁基线提交）**：用户指令「直接调用生图 Skill 首次生成第 5–9 屏五张背景，延续第一轮园林方向，中部净化为文字让路」→ 五条统一系列 prompt 并行生成一次通过零返工（书斋暖光/雨后曲廊/池塘石桥/远山晨雾/明亮庭院，游园动线 4→9 连续，上下缘亮雾白便于滚动衔接；源图 1672×941 存 docs/ui-redesign-2026-10-11/images/home-sections/section{5..9}-*.png）→ 用户指令「接入首页」→ PIL 转码 avif(51–190KB)/webp 入 assets/home/section-{subjects,pathway,pond,horizon,courtyard}；新增 **landing-sections-2.css**（styleUrls 三文件治理预算），映射 #subjects 32%/#process 50%+左廊横向提亮/#voices 55%/.vision 45%（最轻面纱）/.cta 38%；#subjects/#process/#voices 标题柔光+overflow-x:clip，**#process 步骤区裸文字→整区羽化柔光**；移动档五屏面纱加强，CTA 两轮微调压墙基亮带。验收：ng build 零错误零警告；home_sections_walkthrough.py 扩展 8 屏断言全过；1920 逐屏裁切+375 逐屏视口截图复核文字全锐利。**新坑**：CDP Runtime.evaluate 顶层 const 跨调用残留须 IIFE 隔离；captureBeyondViewport 全页拼接在 vh 布局下底部串帧为采集伪影（DOM 探测 heroes=1/footer 正常佐证），移动验收以视口截图为准（新工具 home_mobile_sections_check.py/home_mobile_bottom_check.py）。品牌锁 argv(c6e9213) PASS（876 条，files 纳入 landing-sections-2.css）→ 基线 10f00fe→c6e9213。

**上一轮（v2 中部净化重做 + 替换，24b552d）**：用户要求重做三屏生图，重点「中间区域净化/弱化」为文字让路。三条统一系列 prompt 并行生成一次通过：v2 图景观全部退到边缘角落（左月洞门翠竹/右亭角；左书廊纵深/右下灯笼；左临窗书斋一角/右远月洞门），中央大面雾白粉墙/照壁/水面留白。PIL 转码**同名覆盖** assets/home 三屏 webp/avif（较 v1 更轻），CSS/取景/面纱零改动，走查重跑全过（三屏断言 ✓、375 无溢出 ✓、hero 无损 ✓）。v1 源图保留在 docs/ui-redesign-2026-10-11/images/home-sections/ 不删。

**上一轮（生图 v1 + 接入首页，2 提交）**：① 按用户规格用本地生图 Skill 产出第 2/3/4 屏纯背景图（先读首屏 hero 取锚 → 第 2 屏定锚 → 串行生成 3/4 → 三联画同框验收无断裂）；② `10f00fe` 接入落地：三屏 `#philosophy/#services/#teachers` 背景 image-set（avif 优先），按实渲尺寸（1920 下 574/628/910px）调取景（28%/60%/46%）与面纱；**用户反馈「太模糊」→ 面纱减半提亮**；标题区羽化柔光留白（::before+径向 mask，文字子元素 position:relative 抬层；⚠️ z-index:-1 会被 section 背景盖住）、底部链接玻面 pill、sections overflow-x:clip 修柔光外溢（375 scrollW 407→375）；landing 拆 styleUrls 双文件（新增 landing-sections.css）治理预算，ng build 零警告。→ `a8c3c32` 品牌锁基线 01f8f43→10f00fe（824 条 PASS，files 纳入 landing-sections.css）。验收：tools/home_sections_walkthrough.py 双视口截图断言全过，首屏 hero 零改动无损。源图与对比证据在 docs/ui-redesign-2026-10-11/images/home-sections/。

**分支与提交**：仍在 **`remake__front`** 分支（未推送 origin，合并/推送待用户指令）。本轮登录页重构 4 提交：`600d4e0` 园林摄影背景深化（生图 login-garden + 左区提亮 + 样式拆分）→ `6c7dd1b` 品牌锁基线 16f13f1→600d4e0（files 清单纳入 auth-portal-form.css，锁覆盖 621→752 条 PASS）→ `fa135ba` 用户反馈微调（内容整体右移收拢右侧留白+品牌正文 --text-muted→--text-main 加深加粗）→ `23c34b4` 品牌锁基线 → `22da9f2` 第二轮微调（右移过度回收居中，登录卡加宽 560px + 账号+密码/称呼+密码/院校+学历三组 .field-row 双列对排）→ `041765c` 品牌锁基线 fa135ba→22da9f2（758 条 PASS）→ `01f8f43` **用户规格书布局精修**（1680 栅格 860+100+640、卡片 640×min690、账号密码改回上下单列、导航 76px 与卡片右缘 1824 对齐、四档响应式含 ≤640 表单优先 order:-1；走查抓出并修复柔光 ::before 单栏横向外溢）→ `10ad13f` 品牌锁基线 22da9f2→01f8f43（789 条 PASS）。五视口 CDP 验收工具 `tools/auth_layout_walkthrough.py`（2048 全坐标命中/真实登录冒烟/首页回归）。前一轮内页深化 5 提交 `16f13f1`→`0fb9816`+文档 `7056b88`/`4485971`，见历史快照。

**本轮要点**：用户指认登录页太丑 → 按用户提供的完整提示词用本地生图 Skill（gpt-image-2.5）生成「雨后苏州园林月洞门」全屏背景（左 0-18% 月洞门/中央 20-55% 净墙品牌区/右 55-90% 净墙登录卡区/右上黛瓦檐角）。素材：`frontend/public/assets/auth/login-garden.avif 104KB` / webp 169KB + 移动版 49/85KB（CSS image-set avif 优先 webp 兜底，≤768px 切移动素材）。布局保持品牌左/卡片右恰落画面留白区；登录卡升级玻面白卡（rgba(255,255,255,.86)+backdrop blur 16px+白边 0.7+深影）；三张服务卡玻面 0.62+blur 10px。**用户走查反馈左区底色太深压字** → 修复：水平面纱加强（90deg 0.34→0.58 阶梯+顶部导航带）+ 品牌区羽化柔光留白（`.brand-story::before`：rgba(247,248,244,.42)+backdrop blur 3px+径向 mask 羽化无硬边）。anyComponentStyle 预算治理：auth 样式拆分 component.css（背景/品牌/布局）+ auth-portal-form.css（卡片/表单），styleUrls 双文件。交互零改动（角色 Tab/双通道/注册入驻/就地容错/hero-rise/reduced-motion 全保留）。

**验证**：`npx ng build` 两次零错误零警告（拆分后两文件均低于 8kB 预算）；品牌锁 argv(600d4e0) PASS → 无参 PASS（752 条）；真浏览器（localhost:4200 IAB 新开标签页+禁动画注入）：提亮前后截图对比、computed style 探测（backdrop fixed+面纱、卡片 blur16px、柔光 mask 均生效）、表单冒烟（切家长角色→渠道 pill 变「家长注册」→填 13900000003/Parent#2026→提交按钮启用，**未提交登录零写入**）；首页第一屏回归像素级无损 ✓。375px 实机走查仍挂起（IAB 无 viewport resize API，移动面纱/素材切换仅静态审查 🟡）。

**数据与运行**：8080/4200 用户实例未动；后端零改动、零业务写入、主库 V22 无漂移；生图经本地网关 127.0.0.1:8317（gpt-image-2.5），源图 1200px 预览存 docs/ui-redesign-2026-10-11/images/login-garden-src-preview-2026-10-12.png，根目录中间产物已清理。证据截图：auth-login-garden-2026-10-12.png（终版）、home-regression-after-auth-2026-10-12.png（首页回归）。

### 以下为历史现场快照（仅历史参考）

**内页视觉深化轮（2026-10-12 上午）**：5 提交：`16f13f1` 导师匹配页对齐 16:9 庭院雅境参考图（背景带+眉标+步骤 2 双栏+右栏两卡+导师卡全要素重构）→ `00039b1` 品牌锁基线 38682ba→16f13f1（713 条 PASS）→ `11e17b4` 导师详情页（背景带+右栏平台保障/预约流程+认证角标，CSS 超预算拆 teacher-detail-lists.css）→ `50f70a4` 四端工作台统一 300px 园林背景带 → `0fb9816` 师资检索页 Hero 叠园林意境；文档 `7056b88`/`4485971`。头像坚持真实 monogram 不用虚构照片；全部复用 hero.avif 零新增摄影资产。验证：每次改动均 `npx ng build` 零错误零警告；品牌锁 713 条 PASS × 3；真浏览器（新开 IAB 标签页+注入禁动画）截图验收：match 步骤 1/2 ✓、导师详情首屏/档期/评价（真实 38 条 8 页）✓、四角色工作台（真实数据 89 学员·221 预约·150 订单等）✓、师资页 Hero+4 真实条目 ✓、首页第一屏像素级无损 ✓。全程零业务数据写入（match 仅走到步骤 2）。剩余候选：学习互动页（growth/archive/questions/assessments）、消息/客服/退款进度等功能页视觉统一（视用户要求）、移动端 375px 全站走查（P1 既有项）。


**验证**：`npx ng build` 零错误零警告（anyComponentStyle 超预算 2 件已拆 styleUrls 治理）；`node tools/verify_brand_css.cjs` PASS；真浏览器（localhost:4200，用户 ng serve 实例热重载）走查：学生（专注课程卡/A3 预约列表 121 条/A1 两阶段取消模态 preview 实测退款影响 ¥300 预览，**未点终态 execute**）、家长（绑定子女/钱包 ¥22400/流水）、教师（公开资料/今日课程含开始上课·课堂准备·改期·取消预约按钮/功能卡组）、运营（全景大盘 89 学员·20 家长·17 导师·221 预约·150 订单·0.15 抽成 / 六 Tab 含 D1 平台配置·D2 限流重置）、公共页（/teachers 筛选+卡片、导师详情含 C1 评价区与可约档期、/student/match 四步向导步骤 1→2 推荐真实返回李老师）。截图证据 docs/ui-redesign-2026-10-11/images/。

**数据与运行**：8080/4200 均为用户自行启动的实例（fat-1011.jar 连主库 V21 + ng serve），本轮未触碰；后端零改动（仅前端 + tools/verify_brand_css.cjs 默认值），无新迁移、无资金变动；走查产生的唯一写操作是学生域 A1 模态的 cancel preview（只读计算，未 execute）。

**走查抓出的缺陷与修复**：RevealDirective 依赖 IntersectionObserver（回调按帧派发），无帧环境（无头/嵌入式 WebView、后台节流）回调永不到达→内容被 opacity:0 永久困住；已加 350ms 计时器+scroll/resize 几何兜底（语义等价、真实浏览器不变）。**IAB 走查环境陷阱（新）**：长会话旧标签页会 rAF 停摆（rafTicks=0）、window.scrollTo/scrollIntoView 不生效、fullPage 截图重复拼接首屏——验收用新开标签页 + 注入 `*{animation:none!important}` 截图 + body zoom 缩放逐段截长页。

### 以下为历史现场快照（仅历史参考）

性能轮完成并验证；源码**V22**（新增 V22__public_catalog_keyword_indexes.sql），16:05:40 完整 verify **49单元/架构+162IT 零失败**（loadperf-final-verify2.log；首跑 1 例 review_count NULLS FIRST 失败→修复→重跑全绿，失败日志保留）；新验收库 E2E **220/0**（loadperf-e2e.log）、权限探针 **19拒绝/0漏洞**（loadperf-authz.log）。报告 docs/交付报告_性能与持续负载_2026-10-11.md 与方案 docs/性能验证方案与门槛_2026-10-11.md；性能数据 backend/target/load-perf-results.json（25 次运行）、catalog-explain-after-v22.log、loadperf-e2e-server.log。全部失败日志保留。

**数据与运行**：隔离库 nurture_gap_acceptance_load_perf_20261011（V22，100,000 在售条目/5,000 教师/19,995 评价/24,997 档期/3,000 工单/15,129 消息/600 学习附件元数据，全部合成，种子 tools/load_perf_seed.py）；E2E 验收库 nurture_gap_acceptance_loadperf_e2e_20261011（V22，夹具已消费）。上传目录 backend/target/load-perf-files-20261011、loadperf-e2e-files-20261011。8081 回环性能服务运行中（PID 45856，load-perf-runtime.jar，最终代码 SHA256 C78238FC6E9623BE870E9A6C812F2B2C676934E882302EA374989F5529D89EED，validate 复验 PASS）；8082 E2E 实例（e2e-acceptance-runtime.jar）用后即止。**8080/4200 被用户自行启动的实例占用（nurture-backend-fat-1011.jar + ng serve），本轮未停止未触碰**；构建曾以其文件锁改用临时 finalName（pom 修改即还原）。

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
| P0（待用户决策） | remake__front 分支收口：合并 main 并推送 origin（或继续迭代） | `git log --oneline remake__front`（14 提交 ed0c636→6c7dd1b）；推送前用户确认；推送沿用 xusheng-123 账号 | 远程可见；不回撤历史、不强推 |
| P1（进行中） | 内页视觉深化（总纲 Phase 5，方向 D 令牌内，首页第一屏绝对不动）：① 导师匹配 ✅ 16f13f1 → ② 导师详情 ✅ 11e17b4 → ③-⑥ 四端工作台 ✅ 50f70a4 → ⑦ 师资检索 Hero ✅ 0fb9816 → ⑧ 登录门户园林背景 ✅ 600d4e0+6c7dd1b+fa135ba+23c34b4（含两轮用户走查微调：左区提亮、整体右移+正文加深） | 参考=用户 16:9 庭院雅境内页样图+docs/ui-redesign-2026-10-11/design-system-d.md；每页闭环：实现→ng build→真浏览器截图→提交→首页无损复核；新园林素材用 local-image-gen（127.0.0.1:8317 gpt-image-2.5）生成并优化为 AVIF/WebP；剩余候选=学习互动/消息/客服/退款页视觉统一（视用户要求）+ 共享业务页桌面宽屏统一（learning/messages/support/refunds 现 960–1160px 壳，四角色工作台已 1560，视用户要求） | 截图对照参考图；品牌锁 PASS；零虚构数据/假按钮 |
| P1 | 移动端 375px 全站走查（本轮仅首屏/移动 Hero 验证过） | docs/ui-redesign-2026-10-11/mobile-check.html（8090 服务 iframe 包装） | 无横向溢出、触控热区 ≥44px |
| P1 | 后端配套小缺口（不阻断 UI）：GET /admin/configs 列表端点（D1 目前无回读）、ReviewView 补 createdAt、家长代管预约取消鉴权放开（后端 403 故前端未接） | 见 docs/交付报告_无UI功能清单_2026-10-11.md 与本文件 §3 本轮行 | IT + 前端接真数据 |
| P2 | 性能对比与 Lighthouse 复测（本轮聚焦视觉与交互，LCP/字体子集化未仪器实测） | hero.avif 已 164KB + preload + fetchpriority；宋体回退栈未子集化 | 指标如实记录，不虚构 |
| 已完成（2026-10-11 重构轮） | 补齐「有后端无前端入口」功能：A1/A2/A3 交易闭环 + B1-B7/C1/D1/D2 | 全部随方向 D 重构落地；6 项重复旧入口按调研结论未补 UI | 四角色真浏览器走查通过，见 §4 快照 |
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

- **UI 重构轮（2026-10-11，remake__front）**：品牌语言已由用户拍板从「竹林暗色+楷体」切到方向 D（黛绿暖金浅色+宋体标题），原「不改品牌/深色主题」硬约束自此解除并以 design-system-d.md 为准；品牌锁基线已更新 38682ba（618 条），改品牌仍需走基线更新。分支未推送 origin（待用户指令）；走查仅用 preview 只读接口，未执行取消终态/未建真实预约；家长代管预约取消因后端 403 未接 UI；LCP/字体子集化/移动端全站 375px 未仪器实测。
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
- 本地Git原无远端；2026-10-11已添加远程origin=https://github.com/xusheng-123/myjiajiao.git并推送main（bd8f054）。保护规则不变：文件系统及现有历史需保护，禁止reset/强推/clean。阶段4在ba2ddcd、阶段3在929ca88保护、核心补缺已在b9c7ae4保护，后续阶段提交以git log为准。`tools/settlement_walkthrough.png`接手前已存在。旧报告与表格按历史范围阅读，以最新代码与本节快照为准。
