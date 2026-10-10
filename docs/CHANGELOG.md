# CHANGELOG — 变更与验证记录（新条目置顶）

> 详细叙述见 `E:\线上家教平台\项目交接.md`（滚动事实源）。本表只做索引。

| 日期 | 轮次/条目 | 要点 | 验证 |
|---|---|---|---|
| 2026-10-10 | 质量加固轮：阶段一收官后四项风险核查与修复 | **问题一（下架默认价）**：审计确认「明确存在但已下架」科目下单会回落 ¥300 默认价（G6 E2E 曾断言该缺陷行为）；修复为定价三分支——在售→现价 / 同科目存在下架条目→409「停止销售」/ 从未定价→保留 ¥300 合法兜底；`DelistedCoursePricingIT` 覆盖（快照价不变）。**问题二（过期档期）**：后端查询层过滤——`openSlotsOf` 仅返回未开始的 OPEN（open-slots 与检索 nextOpenSlotAt 共用），教师日历历史保留不删数据；新建预约对过去档期 400 拒绝（事务回滚释放档期，改期路径维持既有）；`StaleSlotGuardIT` 覆盖。**问题三（上传加固）**：资质材料白名单三重校验（扩展名→MIME→文件魔数，PDF/JPG/PNG）伪造类型 400；发现并修复 `max-request-size` 缺省 10MB < 20MB 文件上限的配置矛盾（→45MB）；IdempotencyFilter 识别 Tomcat 原生 FileSizeLimitExceededException 返回 413（原为 403）；前端 accept + 文案同步；**如实记录：无病毒扫描能力**。**问题四（窄屏）**：Edge headless CDP 真实 375px 视口走查（tools/narrow_walkthrough*.py，登录态注入）——找老师页/收藏按钮/收藏面板/筛选器/空态零横向溢出、无遮挡 | verify 全绿（单元 35 + IT 32，新增 2 IT）；E2E 205/205（+4 断言、改 1 断言：回落默认价→409 拒绝）；探针 0；配置门禁 ✓；生产构建 ✓（仅既有 match css 268B 警告）；窄屏 375px 与桌面 1280px 双视口截图核查通过 |
| 2026-10-10 | G10 教师收藏 + 可约时间筛选（**阶段一收官**） | 匹配页三入口新增「③ 我的收藏」：教师卡收藏 toggle（♡/♥、以服务端响应 favorited 为准、防连点、失败不保留虚假成功态、无 localStorage 权威态、刷新从 GET favorites 恢复）；收藏面板=真实列表+无参检索富集（科目/学段/价格/评分/最近可约+「以档期实际确认为准」注记），缺席教师如实标注不可预约原因，可取消；时间筛选 chips 不限/今日/3天/7天：纯前端过滤真实 `nextOpenSlotAt`（今日=当地自然日剩余、3天=now+72h、7天=now+168h，过去/无效/null 筛选启用时排除），computed 不重复请求，空态「当前时间范围内暂无可约教师」；endpoints.ts favorites/toggleFavorite/recommend 从 learning 段归位 matching 段（历史误放零引用）；后端零改动；E2E 190→201（§19 收藏 API 闭环 11 断言） | verify 全绿（单元 35 + IT 30）；E2E 201/201；探针 0；生产构建 ✓（match.component.css 超预算 268B 警告，与 landing 同类不阻塞）；浏览器走查全通过（收藏翻转/刷新恢复/富集列表/取消/四档筛选 0/4→1/4→1/4→4/4 含非空正例/空态/入口切换筛选状态保持）；走查中发现并清理 19 条历史残留过去 OPEN 档期（正规 DELETE API，污染 nextOpenSlotAt 展示）；commit 3b24d28 |
| 2026-10-09 | G5 教师自助资质上传 + G6 课程定价与在售管理 | G5 资质认证卡：multipart 双材料上传（≤20MB 如实标注/防重/文件清单/服务端状态与真实审核意见/驳回可重提）；G6 课程与定价卡：列表+新增（D9 幂等如实提示）+调价（原价展示、仅影响新订单说明）+下架（二次确认、「停止销售」文案、无重新上架入口）；后端零改动；E2E 160→190（G5 +14 / G6 +16）；另修 E2E 三处多轮累计假设（钱包余额基线/账户翻页/待收款翻页） | verify 全绿（单元 35 + IT 30）；E2E 190/190；探针 0；生产构建 ✓（仅既有 CSS budget 警告）；G5/G6 浏览器全生命周期走查通过；commit 4040f1e / ae089af / 02caacf / 65c997e / 0861d92 |
| 2026-10-09 | Git 版本保护 + G3 监护确认入口 + G7 学生/家长下单支付 | 建立本地 git 基线（9631f8d，480 文件，.gitignore 排除密钥/构建产物/备份，.env.example 保留）；G3 `GET /parent/me/children:pending` + 学生工作台申请卡；G7 `GET /order/bookings:orderable`（监护家长视角+服务端定价）+ `GET /payment/config`（沙箱开关）+ 学生下单入口 + 家长「课程与订单」卡（选孩子→下单→沙箱演示支付/模式 B 引导）；生产模式冒烟 sandbox=false 通过 | verify 全绿（单元 35 + IT 30）；E2E 160/160（G3 +9 / G7 +27）；探针 0；生产构建 ✓；G3/G7 浏览器全链路走查通过；commit 7979522 / 3f6da7d |
| 2026-10-09 | 阶段一收口：G9 测试 + G1/G2/G4/G8 四入口 | G9 `LearningGrowthIT` 9 用例（IT 19→28）；G1 运营收款（补 `GET /admin/orders/pending-payment` + 订单卡，修 ngOnInit 漏调）；G2 家长周报（修 studentId 档案 ID→账户 ID 语义缺陷）；G4 学生评价（补 `lesson-history` + `reviews-given`，修 tags 懒加载 500 与课程表断点）；G8 进入课堂（协议白名单+弹窗拦截降级）；E2E 114→124 | verify 全绿（单元 35 + IT 28）；E2E 124/124；探针 0；生产构建 ✓；四入口浏览器实测全通过 |
| 2026-09-30 | 项目交接机制建立 | 新增 PROJECT_HANDOFF.md + docs/ 四件套 + AGENTS.md 维护规则；对 18 页进度做程序化核实 | 本文件即为产物；状态核实记录见 PROJECT_HANDOFF §4 |
| 2026-09-28 | 学习成长升级 | learning 新模块（4 实体）+ V9/V10 + matching 收藏/推荐 + 3 个学生端组件 + 19 个新 API | verify 全绿（单元 35 + IT 19）；E2E 114/114；探针 0；生产构建 ✓；⚠️ V9 漏 favorite_teachers.updated_at 由 V10 修复（IT 抓出） |
| 2026-09-28 | Motion 精修 + 呼吸感升级 | 微雾/微光/涟漪/视差/墨线/卡片呼吸；零新依赖 | 桌面/移动/视差/hover 实测；生产构建 ✓；reduced-motion 三层 |
| 2026-09-28 | 交付物日 | 答辩 PPT v2（四幕重构+QA 清零）· 项目计划书 docx · 进度与上线事项 docx · 小白测试指南 docx · 一键启动 bat ×4 | postcheck/文档 QA 全清 |
| 2026-09-28 | 视觉修复系列 | Hero 竹叶前景（三次迭代终版：叶层 z-60 独立 + 无 mask 自裁剪）+ 竹林构图纠偏 | 浏览器截图对比 + clip 细节验证 |
| 2026-09-28 | STAGING 收口 | 双主机名 bundle 验证（build:staging/pages）、env 模板、staging 覆盖层（修沙箱压制冲突）、RUNBOOK 重写、部署包 61MB | verify_config 38 项 + compose config 渲染 |
| 2026-09-28 | 双专项实测 | Backup→Restore Drill（RTO 55s PASS）；支付模式 B 状态机 + 6 并发实测（修 createAdminAccount 硬编号 P1） | 离线支付 IT 8+1、E2E 第 16 节 |
| 2026-09-27 | Release Implementation | P0 清零（备份/actuator/支付 OFFLINE+V8/env 模板）+ P1 十项收口 + RUNBOOK + verify_config 24 项 | 单元 35 + IT 10 + E2E 108 |
| 2026-09-27 | 架构收口 | ArchUnit 空基线（升级 1.5.1）、跨模块 infra 22→0、循环切断、DTO 化、V7 | verify 全绿 |
| 2026-09-27 | 维护轮 1–3 | D1–D12（绑定授权/状态闸门/轮换/沙箱验签/幂等指纹/Token 哈希/改期/提醒）、19 项授权漏洞修复、站内信 BOLA | 单元 28→32、IT 5、E2E 78→87、探针 0 |
| 2026-09-27 | 功能基线 | 16 模块闭环、四角色门户、/teachers、竹林视觉、P0 安全三项（限频/防爆破/支付验签） | E2E 63→78 |
| — | **当前测试基线** | 单元 35 · 集成 32 · E2E 205 · 探针 0 · 配置门禁 24 项 · RTO 55s | 见 PROJECT_HANDOFF §3 |
