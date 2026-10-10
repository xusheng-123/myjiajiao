# 待产品决策：交易规则分析报告（试听收费 × 改期时间规则）

> 日期：2026-10-10 ｜ 基线：main @ `0823e87`（阶段一收官 + 质量加固完成后，工作区干净）
> 性质：**只读分析**——本轮零代码/零配置/零数据库改动，所有结论均以当前实际代码与测试为依据并附文件路径；历史报告结论未被直接复制，与代码不一致处已单独标注（见 §1.3）。
> 关联文档：已定决策见 `docs/DECISIONS.md`；本文档只承载**待负责人拍板**的事项。决策完成后请填写 §6 并将结论同步至 `docs/DECISIONS.md`。

---

## 1. 当前实现事实（已核查）

### 1.1 试听预约与订单的真实资金链路

| # | 事实 | 依据（代码/接口/测试） |
|---|---|---|
| 1 | 预约类型仅 `TRIAL`/`FORMAL` 两种；**请求不传 `bookingType` 时默认即 TRIAL** | `BookingController.java:76-79`（`"FORMAL".equalsIgnoreCase(...)?FORMAL:TRIAL`）；`Booking.java:26` |
| 2 | **试听预约自动确认**（跳过教师审批），预约侧金额恒记 0 分 | `BookingApplicationService.java:61-65`（`saved.confirm()`，注释「试听课免费」）；FORMAL 走 `approveAsTeacher`（:74-79） |
| 3 | **下单资格只看「CONFIRMED」不看预约类型**——试听预约自动确认后立即满足下单条件 | `OrderApplicationService.createForCaller:69-91`（仅 `"CONFIRMED".equals(booking.status())`） |
| 4 | **订单类型硬编码 `FORMAL_LESSONS`**；枚举 `OrderType.TRIAL_LESSON` 在主代码中**零使用**（死代码） | `OrderApplicationService.java:89`；`Order.java:27`（grep 全仓仅枚举定义一处） |
| 5 | 订单金额与正式课**完全同一套定价**：在售现价 / 已下架 409 / 从未定价 ¥300 兜底；**不存在任何「试听价」概念** | `OrderApplicationService.resolvePriceCents:206-217`；`SubjectOffering` 仅有 `pricePerHourCents` 一个价格字段 |
| 6 | **前端明确向学生承诺「免费试听」「试听不收费」「试听自动确认」** | `match.component.html:299-303`（「类型：免费试听」「¥X/小时（试听不收费）」） |
| 7 | 学生端课程表「下单」按钮、家长端「可下单课程」列表**均不区分试听**；家长可下单列表为服务端驱动且含试听 | `student-dashboard.component.ts:160-166`；`orderableLessonsForCaller:148-166`（过滤条件=CONFIRMED+无订单）；E2E `tools/e2e_test.py:432-437` 断言试听预约出现在可下单视图且 `priceCents>0` |
| 8 | 试听订单支付走与正式课**同一条资金链路**：钱包扣款 → 订单 PAID → **生成教师课酬结算条目**（毛额×(1−抽成)）→ 双向通知；模式 B 线下确认同样生成结算 | `PaymentApplicationService.executeSuccess:87-111`；`confirmOfflinePayment:161-196`；`SettlementApplicationService.createEntryForPaidOrder:29-38`（抽成缺省 0.15，平台配置可调） |
| 9 | 取消已付订单自动**全额退款**至家庭钱包（试听/正式同一规则） | `BookingApplicationService.executeCancel:175-189` → `RefundApplicationService.executeForBookingCancellation:72-86`；E2E §9 断言 +20000 原路入账 |
| 10 | 防重复下单：一课一单（`findByBookingId` 唯一性校验 409）+ 幂等键防重放，IT/E2E 均有覆盖 | `OrderApplicationService.java:84-86,222-224`；E2E「G7 一课一单：重复下单被拒(409)」；`OrderPaymentFlowIT` |
| 11 | **无任何「每生每师一次试听」类限制**——同一学生可对同一教师无限次预约试听（每次占用一个档期） | `BookingApplicationService.create` 无历史试听查询；`BookingRepository` 无相关计数方法 |
| 12 | 大量既有测试以 TRIAL 作为「自动确认」夹具构造 CONFIRMED 预约，**随后对其下单/改期/取消** | E2E §6/§8.4/§8.5（改期）/§8.6/§15.3；IT：`OrderPaymentFlowIT:51`、`BookingRescheduleIT:52-65`、`DelistedCoursePricingIT`、`GuardianBindingOrderAuthIT`、`LearningGrowthIT` 等 |
| 13 | 上课提醒调度器**不区分**试听/正式（CONFIRMED 全部提醒）；教师「今日课程」同样包含试听 | `LessonReminderScheduler.java:54`（`findByStatus(CONFIRMED)`）；`todayLessonsForTeacher:204-218` |
| 14 | 代码中**已存在**的试听相关规则仅两处：①预约侧金额 0；②取消时「预约无订单属合法业务态（试听课）」的特殊事务处理 | `BookingApplicationService.java:60-61`；`OrderApplicationService.getByBooking:127-132`（`noRollbackFor` 注释明确写「试听课」） |

### 1.2 改期与新建预约的时间规则对比

| 维度 | 新建预约（create） | 改期（reschedule） | 依据 |
|---|---|---|---|
| 状态门槛 | 任意（新建即 REQUESTED/TRIAL 直接 CONFIRMED） | 仅 CONFIRMED（含「已经开始甚至已过去」的 CONFIRMED） | `BookingApplicationService.java:127-129` |
| 档期归属校验 | ✅ `holdSlot` 校验属于同一教师 | ✅ 同左 | `SchedulingApplicationService.holdSlot:67-75` |
| 档期状态校验 | ✅ 仅 OPEN 可锁 | ✅ 同左（HELD/BOOKED/BLOCKED 均拒绝） | `AvailabilitySlot.hold():57-60` |
| **时间校验** | ✅ `startAt < now` → 400（质量加固 2026-10-10 新增） | ❌ **无任何时间校验**——过去 OPEN 档期可作改期目标（档期发布接口也允许发布过去时间） | `BookingApplicationService.java:56-59` vs `reschedule:117-142` |
| 事务与回滚 | holdSlot 失败/时间校验失败 → 事务回滚释放档期 | ✅ 先锁新（holdSlot）→ 再放旧（releaseSlot）→ 改引用；任一步失败整体回滚、原档期保留（IT 有「冲突 409 且原档期保留」用例） | `reschedule:133-136`；`BookingRescheduleIT.改期到已被其他预约占用的档期_冲突409且原档期保留` |
| 同档期拒绝 | — | ✅ 409 | `reschedule:130-132`；E2E §8.5 |
| 参与方授权 | 仅学生本人 | ✅ 双方或运营；无关用户 403（IT 覆盖） | `reschedule:119-126`；`BookingRescheduleIT` |
| 时区/时钟 | `OffsetDateTime.now()` 服务器本地偏移，档期比较为 offset-aware 的绝对时刻比较；两者完全一致 | 同左（无新增时区逻辑需引入） | 全项目统一 `OffsetDateTime` |
| 「刚开始」边界 | `isBefore` 严格判定：`startAt == now` 可约，已过 1 秒即拒绝 | 目标档期无校验；**源预约已开始/已过去仍可改期**（只查状态） | `create:57`、`reschedule` 无对应判断 |

**并发事实**：档期实体有 `@Version` 乐观锁（`AvailabilitySlot.java:41-43`），同槽并发锁定一胜一败；**但 `Booking` 实体无乐观锁**（`AggregateRoot` 无 `@Version`，grep 证实）——同一预约并发改期到两个不同档期时，两个事务可能都成功（各自锁新槽、放旧槽），后提交者覆盖 `slotId`，先提交者锁到的档期沦为**永久 HELD 的孤儿占用**（教师可手动释放，但无自动回收）。

**改期到过去的影响面**（若发生）：教师「今日课程」按当天窗口过滤将不再显示该课；课前提醒（60s/30min 窗口）不会触发；但课程仍可 start→complete→评价→生成学习记录，已付订单对改期后课程仍有效——即产生「系统里存在一门已过去的课却无人被提醒」的幽灵课时。`messagingService.notify(RESCHEDULE)` 通知仍会发出（`reschedule:137-140`）。

### 1.3 与历史报告的差异（如实指出）

1. 上轮交付报告称「改期到过去时间维持既有行为」——属实，但**未指出**改期源预约（已开始的课）与目标档期是两个独立维度：真正缺失的校验只在**目标档期**；「把已错过未取消的课改到未来」是改期的核心补救价值，不应一并禁止（详见 §4.2 推荐方案）。
2. 上轮报告未披露本报告 §1.1-8/9 的资金链路细节：**退款不回冲结算条目**——`markSettled`/`markPaidOut` 在主代码中零调用点（结算条目终身 PENDING），且 refund/booking 模块无任何 settlement 引用（grep 证实）。退款仲裁 Preview 的文案「教师课酬结算条目作废」（`RefundApplicationService.java:102`）**与代码行为不符**。此缺口独立于试听决策，但直接影响 §3 中各方案的资金风险评估。
3. 附带发现（代码审查，🟡 未实测，本轮禁止写操作故未验证）：`SubjectOffering` 的**调价**路径有正数校验（`reprice:47-50`，E2E 覆盖），但**新增课程**路径（构造器 `SubjectOffering:40-46` + `addSubject:99-107`）未见价格校验——理论上可创建 0 元或负价在售课程。建议随任一方案实施时顺带补一行业务校验（列入 §5）。

---

## 2. 问题与影响

### 2.1 试听收费

**当前行为**：前端承诺「免费试听」，学生据此预约试听（自动确认）→ 学生/家长端立即出现「下单」入口 → 一键生成按正式课定价的应付订单 → 支付后进入教师课酬结算。

**差异与影响**：

| 角色 | 影响 |
|---|---|
| 学生/家长 | 在「试听免费」的明确文案承诺下被引导创建付费订单——若未细看金额直接支付，构成**违背明示承诺的收费**；投诉与信任风险 |
| 教师 | 收到从未谈妥的试听订单课酬入账通知；若后续退款，课酬条目不回冲（§1.3-2），账目与实际服务不符 |
| 运营/财务 | 对账快照中试听预约关联的是 `FORMAL_LESSONS` 订单，`TRIAL_LESSON` 类型从未使用——**无法从订单维度区分试听转化与直接售课**，试听转化率（核心运营指标）不可统计 |
| 平台 | 「免费试听」是获客漏斗的入口承诺；当前实现使该承诺不可兑现，或只能在「悄悄收费」与「人工拦截下单」间二选一 |

### 2.2 改期时间规则

**当前行为**：新建预约已拒绝过去档期（2026-10-10 质量加固），但改期仍可：①把课程改到**过去的 OPEN 档期**（含历史残留档期）；②操作**已经开始/已过去**的 CONFIRMED 课程（这个方向是合法补救场景，应保留）。

**影响**：幽灵课时（§1.2 影响面）；与新建规则不一致造成「同一时间概念两套标准」；测试夹具可用过去档期绕过时间语义。风险等级低（需要教师先发布过去档期 + 主动选择），但规则一致性是第二阶段新功能（对比页展示「最近可约」并直达预约/改期）的前提。

---

## 3. 可选方案比较

### 3.1 决策一：试听收费

| 维度 | 方案 A：试听免费（禁止对试听下单） | 方案 B：试听收费（维持现状语义化） | 方案 C：独立试听价/平台补贴 |
|---|---|---|---|
| 用户体验 | ✅ 兑现已有「免费试听」承诺；试听零门槛 | ❌ 与现有文案承诺直接冲突，需重写文案+重新教育用户；或保留文案则构成误导 | ✅ 灵活（优惠试听/限次）；⚠️ 规则复杂需解释 |
| 技术改造范围 | **小**：下单闸门拒绝 TRIAL（1 处校验）+ 可下单视图过滤 TRIAL + 视图补 type 字段供前端隐藏按钮；后端不触及支付/退款/结算 | **最小**（后端零改动）：仅需删除/修改「免费试听」文案；把 `TRIAL_LESSON` 死枚举清理或启用 | **大**：新增试听价字段（**需数据库迁移**）+ 教师定价 UI + 下单定价分支 + 结算/补贴规则 |
| 数据/资金影响 | 试听彻底零资金流：无订单、无支付、无结算、无退款——链路最干净 | 试听产生与正式课同价的完整资金流；免费承诺作废 | 补贴需平台出资记账规则（当前无补贴机制）；独立价需新快照语义 |
| 与现有架构兼容性 | ✅ 完全兼容：代码注释三处（`create:60`、`getByBooking:127`、`Booking.java` 侧 0 元）均体现「试听无订单」的原设计意图，本方案是**补全缺失的闸门**而非改变设计 | ✅ 兼容但与领域模型意图相反（0 元预约侧金额、死枚举 TRIAL_LESSON、取消流的「试听无订单合法态」特判都暗示原设计是免费） | ⚠️ 需扩 `SubjectOffering` 或新表；结算需区分「补贴承担方」 |
| 所需测试 | 修改既有夹具（TRIAL→FORMAL+审批流，约 5-6 处 IT/E2E 场景）+ 新增「试听下单被拒」用例 | 修改前端文案断言；新增「试听可按正式价下单」显式断言 | 全套新测试（定价/下单/结算/补贴）+ 迁移测试 |
| 潜在风险 | ①E2E/IT 夹具改造成本（一次性，属语义修正）②免费试听滥用（无每师一次限制，**需配套限次规则**，见待确认问题） | ①违背已公开承诺的产品信誉风险 ②运营无法统计试听转化 ③「试听」沦为「第一节正价课」 | ①改造面最大、触及结算规则 ②补贴预算需商务决策 |
| 需要数据库迁移 | **否** | **否** | **是**（新定价字段/表） |
| 需要额外产品/商务决策 | 试听是否限次、教师是否有试听补贴 | 「免费试听」承诺如何撤回、对外口径 | 补贴金额与承担方、试听定价策略 |

### 3.2 决策二：改期时间规则

| 维度 | 方案 A：目标档期必须是尚未开始 | 方案 B：按课程当前状态动态决定 | 方案 C：例外白名单 |
|---|---|---|---|
| 用户体验 | ✅ 与新建预约规则一致，易理解；保留「把已错过的课改到未来」的补救能力（源预约不限时间） | ⚠️ 规则随状态变化（如 IN_PROGRESS 可改/CONFIRMED 不可改），用户难预期 | ✅ 灵活但需运营介入 |
| 技术改造范围 | **极小**：`reschedule` 在 holdSlot 后加一行与 `create:57` 完全相同的校验 → 400 | 中：需定义每状态的时间规则矩阵 + 文案 | 中：例外需新参数/角色权限 + 审计 |
| 数据/资金影响 | 无（纯校验；订单不受改期影响的事实不变） | 无 | 无 |
| 架构兼容性 | ✅ 与 create 完全同构，不触及锁定顺序/事务/通知 | ✅ 但逻辑分散 | ⚠️ 需新授权面 |
| 所需测试 | `BookingRescheduleIT` 加「改期到过去档期被拒」「原档期保留」两用例 + E2E 1 断言 | 全矩阵测试 | 例外路径专项测试 |
| 潜在风险 | 几乎无——过去档期本就不该作为目标（可约展示层已在质量加固中过滤了过去档期，改期目标来自可约列表，正常路径根本选不到过去时间；本方案是防伪造请求的兜底） | 状态×时间矩阵的边界（IN_PROGRESS 改期到过去？）复杂易漏 | 例外滥用 |
| 需要数据库迁移 | **否** | **否** | **否** |
| 需要额外产品/商务决策 | 无（规则自明） | 需定义各状态规则 | 需定义例外条件与审批人 |

---

## 4. 推荐决策

### 4.1 决策一（试听收费）：**推荐方案 A——试听免费，禁止对试听预约创建订单**

**推荐理由（技术层面）**：

1. **产品语义已公开承诺**：match 向导明示「免费试听/试听不收费」（§1.1-6）。方案 A 是兑现承诺，方案 B 是撤回承诺——撤回承诺是产品决策，不是技术默认值；
2. **与领域模型原设计意图一致**：预约侧 0 元、取消流「试听无订单属合法态」特判、`TRIAL_LESSON` 死枚举——三处代码证据指向原设计就是「试听无订单」，缺失的只是下单闸门；
3. **改造面最小且完全避开交易状态机**：不生成 0 元订单（0 元支付/0 元结算/0 元退款链路从未被测试，主动绕开），新增校验均为前置拒绝；
4. 运营额外收益：试听从此与订单解耦，试听转化可从「预约类型 × 后续 FORMAL 预约」清晰统计。

**需项目负责人确认的关键问题（未确定的产品规则，技术方案不替你决定）**：

1. 试听免费是否为**长期政策**（拉新策略），还是阶段性政策？
2. 是否需要**每学生对同一教师仅一次免费试听**的限次规则？（当前无任何限制；若不限次，免费试听可被反复用于白嫖档期——建议至少限次，限次数值请拍板）
3. 免费试听教师**是否有补贴**？（当前平台无补贴机制，默认无；若要有，属新商务规则需单独设计）
4. 试听是否仍**自动确认**？（自动确认与免费组合意味着学生可无门槛占用教师档期；改为需教师审批则改动较大，默认维持自动确认）
5. 存量已产生的「试听预约 × 付费订单」（测试/演示数据）是否需要处理？（默认：不动，价格快照不回写，与既有原则一致）

### 4.2 决策二（改期时间）：**推荐方案 A——改期目标档期必须尚未开始（源预约时间不做限制）**

**推荐理由（技术层面）**：

1. 与新建预约的时间语义统一（同一「可约时间」概念一套标准）；可约展示层在质量加固后已不展示过去档期，正常路径选不到过去时间，本校验是防伪造请求的最后防线；
2. **只约束目标、不约束源**：「老师临时有事，把已开始/已过去的 CONFIRMED 课改到未来」是改期存在的核心价值，必须保留；
3. 改动为一行校验（与 `create:57` 同谓词），不触及锁定顺序、事务边界、通知与订单；
4. 边界清晰：目标 `startAt < now` → 拒绝（400，与 create 同文案风格）；`startAt == now` 允许（与 create 一致）；时区沿用全项目 `OffsetDateTime.now()` 服务器本地，无新增逻辑。

**需项目负责人确认的关键问题**：

1. 是否认可「已开始/已过去的课程可以改到未来」继续保留（推荐保留，无资金影响）？
2. 改期是否需要次数/时限限制（如课前 2 小时内不可改）？（当前无限制，本轮不建议加——属新增业务规则，如需要请单独拍板）

### 4.3 独立记录：结算回冲缺口（不属两项决策，但影响资金正确性）

退款（取消/仲裁）不回冲已生成的课酬结算条目（§1.3-2）——教师侧 `pendingNetCents` 含已退款课时的酬劳，一旦未来上线提现（`markPaidOut` 已实现但零调用），可能出现「对已退款课时提现」。**完整整改方案见 §9（2026-10-10 分析完成，等待负责人确认资金规则后单独实施，本轮未改动任何资金代码）。**

---

## 5. 实施计划（仅计划，本轮不执行）

### 5.1 方案 A（试听免费）——若获批

| 项 | 内容 |
|---|---|
| 后端模块 | `order`：`createForCaller` 加预约类型闸门（TRIAL → `IllegalStateException`「试听课不产生付费订单」，409）；`orderableLessonsForCaller` 过滤 TRIAL |
| 视图配套 | `BookingApplicationService` 的 `UpcomingBookingView`/`BookingOrderView` 补 `type` 字段（学生课程表隐藏试听行的「下单」按钮） |
| 前端 | `student-dashboard`：试听行不显示「下单」；match 向导文案**无需改动**（已承诺免费，方案 A 即兑现） |
| 可选限次（若负责人批准） | `BookingApplicationService.create`：TRIAL 时查 `existsByStudentIdAndTeacherIdAndType`（新 repository 方法）→ 已有一次则 409「该教师免费试听已使用」 |
| 顺带修复（建议） | `SubjectOffering` 构造器/addSubject 补价格正数校验（§1.3-3） |
| 测试改造 | 夹具迁移：`OrderPaymentFlowIT`、`DelistedCoursePricingIT`、`GuardianBindingOrderAuthIT`（如涉）、E2E §8.4/§8.6 的「TRIAL→下单」改为「FORMAL→教师 approve→下单」；**断言只增强不放松**；新增：试听下单 409、试听不出现在可下单视图、（若限次）二次试听 409 |
| 验收标准 | verify 全绿（单元+IT）；E2E 全绿；探针 0；ng build ✓；走查：match 试听向导→学生课程表无下单按钮→家长可下单列表不含试听；文档四件套同步 + `docs/DECISIONS.md` 落决策 |
| 明确不动 | 支付状态机、退款规则、结算逻辑、既有订单价格快照、FORMAL 审批流 |

### 5.2 方案 A（改期目标须未来）——若获批

| 项 | 内容 |
|---|---|
| 后端 | `BookingApplicationService.reschedule`：holdSlot 后加 `if (slot.getStartAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("不能改期到已过去的时段")`（400；事务回滚释放新档期、原档期保留） |
| 前端 | 无需改动（改期入口列出的是可约 OPEN 档期，过去档期已不可选；本方案为 API 兜底） |
| 测试 | `BookingRescheduleIT` +2 用例：改期到过去 OPEN 档期被拒且原档期保留；已开始的 CONFIRMED 课程改到未来成功（锁定补救场景不回归）；E2E §8.5 +1 断言 |
| 验收标准 | verify/E2E 全绿；探针 0；文档同步 |
| 明确不动 | 锁定顺序（先锁新后放旧）、事务边界、通知、取消/支付状态机；并发孤儿 HELD 槽问题单独记录不并入（见 §4.3 同类的独立缺陷单建议） |

### 5.3 教师对比界面（第二阶段首项候选，本轮不开发）

见 §7。

---

## 6. 决策确认记录

### 事项一：试听收费政策 —— ✅ 已决策（2026-10-10，已实施）

| 字段 | 内容 |
|---|---|
| 最终选择 | ✅ **方案 A：试听免费（禁止对试听预约下单）** |
| 限次规则 | **未实施**，保留为待产品确认的独立规则（每生每师限次数值待拍板） |
| 教师补贴 | 无（平台当前无补贴机制，维持） |
| 决策日期 | 2026-10-10 |
| 决策依据 | 前端已公开承诺「免费试听」；预约侧 0 元/取消流特判/死枚举三处代码证据指向原设计即免费；零金额订单链路未验证故不采用 |
| 是否批准实施 | ✅ 已实施并验证：`TrialFreeRuleIT` + 4 个 IT 夹具迁移 + E2E 断言；决策已录入 `docs/DECISIONS.md` |

### 事项二：改期时间规则 —— ✅ 已决策（2026-10-10，已实施）

| 字段 | 内容 |
|---|---|
| 最终选择 | ✅ **方案 A：目标档期必须尚未开始（源预约不限，补救场景保留）** |
| 已开始课程可否改到未来 | ✅ 可以（推荐项采纳） |
| 决策日期 | 2026-10-10 |
| 决策依据 | 与新建预约同一时间语义；一行校验不动事务结构；并发改期经真实并发 IT 实证无孤儿 HELD（既有档期乐观锁足够） |
| 是否批准实施 | ✅ 已实施并验证：`BookingRescheduleIT` +2 用例、单测 +1 用例、`RescheduleConcurrencyIT` 3 轮真实并发；决策已录入 `docs/DECISIONS.md` |

---

## 7. 附：教师对比界面评估（第二阶段首项候选）

**1. 字段是否足够？——核心字段够，三处缺口需小补**

| 对比维度 | 现状 | 来源 |
|---|---|---|
| 价格/小时、科目、学段 | ✅ 检索候选已有 | `MatchingApplicationService.TeacherCandidate:183-187` |
| 评分 + 评价数 | ✅ 已有 | 同上（`reviewService.statsOf`） |
| 认证状态 | ✅ 已有 | 同上 |
| 最近可约时间 | ✅ 已有（且质量加固后保证为未来时间） | `openSlotsOf` 过滤后 |
| **教学风格 tags** | ❌ 候选恒为空列表（`toCandidate` 硬编码 `List.of()`，`MatchingApplicationService.java:146`）；数据本身存在（`teacher_style_tags` 表，教师档案可维护） | 需在检索组装时从 TeacherProfile 填充 |
| **经验（教龄/学历/学校）** | ❌ 候选不含；数据在 `TeacherProfile`（`yearsOfTeaching/highestDegree/school/intro`）；**无任意教师的公开详情端点**（教师端点全部 `/teacher/me/*`） | 需新增 teacher.app 富集查询 |
| **教师姓名** | ❌ 检索候选不含 displayName（收藏列表经 `identityService.displayNameOf` 富集有）；无名字的对比表不可用 | 同收藏富集模式，改动很小 |

**2. 复用哪些已有组件/接口/数据**：`GET /matching/teachers:search`（主数据源）+ `identityService.displayNameOf`（姓名富集，同收藏面板）+ `reviewService.statsOf`（已在检索内）+ match 页现有卡片/chips 样式体系 + G10 的筛选/收藏状态管理（signals + computed）。

**3. 数据一致性缺口**：①检索候选按「科目条目」展开（同一教师多个科目出现多行）——对比「教师」维度需按 teacherId 去重聚合；②教师未维护档案时 styleTags/yearsOfTeaching 为空——对比表需如实显示「未填写」而非留白或虚构；③`nextOpenSlotAt` 为 null（无可约档期）的教师对比时该格显示「暂无可约」，不参与「最近可约」排序。

**4. 推荐交互方案**：match 页教师卡增加「加入对比」按钮（最多 3 名，超出提示先移除）→ 页面底部/浮层出现对比栏 → 展开并排对比表（行=维度：价格/评分/评价数/教龄/学历/风格 tags/最近可约/认证；列=教师），每行最优值高亮；「最近可约」列点击直达该教师预约向导。纯前端渲染 + 一次检索富集，无需新端点（若做姓名/风格/教龄富集则 search 响应补三个字段，属**增量非破坏**）。

**5. 实施难度与验收标准**：**低-中**（纯前端为主 + 检索响应 3 字段增补；无迁移、无新表、不触交易链路）。验收：375px 窄屏零溢出（复用 narrow_walkthrough 工具）+ 桌面走查 + E2E 对比交互断言（加入/移除/超限提示/字段如实显示）+ 检索契约回归（新字段可空不破坏既有消费方）。

**6. 是否应作为第二阶段首项？——建议：是，但排在两项交易决策落定之后**。理由：①对比表将直接展示价格与「最近可约」并引导用户进入预约/下单——试听是否免费、改期规则是否收紧直接决定对比页的文案与跳转行为，先定规则可避免 UI 二次返工；②其自身改造不触交易链路、风险低，两项决策的实施（若批准）改动面同样可控，顺序上先决策后功能最稳。

---

## 8. 本轮核查清单（供复核）

只读核查覆盖：`BookingController` / `BookingApplicationService` / `Booking` / `AvailabilitySlot` / `SchedulingApplicationService` / `OrderApplicationService` / `Order` / `PaymentApplicationService` / `SettlementApplicationService` / `SettlementEntry` / `RefundApplicationService` / `SubjectOffering` / `TeacherProfile` / `TeacherController` / `MatchingApplicationService`（search/favorites/recommend）/ `MatchingController` / `LessonReminderScheduler` / `AggregateRoot`（乐观锁）；IT：`BookingRescheduleIT` / `OrderPaymentFlowIT` / `DelistedCoursePricingIT`；E2E：§6/§8.4/§8.5/§8.6/§9/§15.3；前端：`match.component.{ts,html}` / `student-dashboard` / `parent-dashboard`。Git：main @ `0823e87`，工作区核查时干净，**本轮未产生任何业务代码/配置/数据库改动，未执行 Git 提交**（本文件为本轮唯一新增文档）。

---

## 9. 任务 D：退款与结算回冲整改方案（2026-10-10 分析完成，**待负责人批准后实施，当前零代码改动**）

> 状态：**待决策**。本节仅为设计与影响分析，不构成已实施事实；实施前须由负责人确认 §9.5 的资金规则问题。

### 9.1 现状与根因（已核查代码）

- **结算条目生成**：`SettlementApplicationService.createEntryForPaidOrder` 仅两处调用——`PaymentApplicationService.executeSuccess`（网关回调链）与 `confirmOfflinePayment`（模式 B 线下确认）。条目含 `bookingId`/`orderId` 关联，`netCents = gross − fee`（构造时定死），初始状态恒 `PENDING`。
- **退款路径**：①取消自动退款 `RefundApplicationService.executeForBookingCancellation`（booking.app 两阶段取消调用）；②仲裁退款 `executeArbitration(approve=true)`。两条路径**均不触碰结算条目**（refund/booking 模块零 settlement 引用，grep 证实）。
- **状态机空转**：`markSettled`/`markPaidOut` 在主代码零调用 → 现阶段所有条目终身 `PENDING`；提现功能未上线。
- **关联键与既有防护**：`ux_settlement_booking`（booking_id 唯一）→ 一课一单恰一条结算条目；`ux_refund_cases_order`（V7，order_id 唯一）→ **同一订单不可能存在两条退款案件，双重退款窗口已被既有约束排除**（此项为既有防护良好面，无需整改）。
- **文案与代码不符**：仲裁 Preview 描述「教师课酬结算条目作废」（`RefundApplicationService.java:102`）——代码无对应行为。

### 9.2 各情形业务后果（按当前行为推演）

| 情形 | 当前后果 | 风险评级 |
|---|---|---|
| 全额退款（取消已付订单） | 家庭钱包全额回款 ✓；教师 `PENDING` 条目原样保留 → `summaryOf.pendingNetCents` 含已退款课时酬劳 | **高**（提现上线后=教师可就已退款课时提现，平台直接资金损失） |
| 部分退款（仲裁 `evaluate` 可设低于实付的 computedAmount） | 钱包按仲裁额退款；教师条目完全不动 → 酬劳与实收完全脱钩 | 中（当前无部分退款 UI，仅仲裁路径可构造） |
| 重复退款 | `ux_refund_cases_order` 唯一约束 + 预约一次性取消 → 已被排除 | 低（既有防护有效） |
| 退款时条目已 SETTLED/PAID_OUT | 现阶段不存在（状态机空转）；提现上线后必然出现——已付给教师的钱无法靠「作废」收回 | 高（设计必须覆盖） |
| 未来提现 | 若不整改，提现净额含已退款课时 → 账实不符 | 高（本整改应作为提现上线的**前置条件**） |

### 9.3 两个处理方向对比

| 维度 | 方向一：作废（原条目标 VOIDED 状态） | 方向二：红冲（新增负向条目，**推荐**） |
|---|---|---|
| 实现 | 原条目加 VOIDED 状态 + 退款时切换 | 新增 `entry_kind=REVERSAL` 负向条目（gross/fee/net 均为负，关联原条目） |
| 可追溯性 | ⚠️ 原条目被「就地改态」，原始金额仍在但账务动作不可见 | ✅ 只增不改：原始 earning 与红冲各自独立成行，完整审计链 |
| 已提现场景 | ❌ 无法处理（钱已付，条目不能「作废」） | ✅ 红冲负条目挂 `PENDING` → 从后续提现净额中自然扣回 |
| 汇总正确性 | 需在 summary 排除 VOIDED | ✅ 汇总=全部条目净额代数和，逻辑最简 |
| 迁移 | 加状态枚举值（需同步 CHECK 约束，V8 先例） | 加 `entry_kind` 列 + `reverses_entry_id` + 部分唯一索引 |

### 9.4 实施方案（方向二，待批准后执行）

1. **迁移 V11**：`settlement_entries` 增 `entry_kind`（EARNING/REVERSAL，默认 EARNING，CHECK 约束同步 ALTER）+ `reverses_entry_id`（nullable）；`CREATE UNIQUE INDEX ux_settlement_reversal ON settlement_entries(reverses_entry_id) WHERE entry_kind='REVERSAL'` ——一原条目至多一条有效红冲（幂等兜底）。
2. **领域层**：`SettlementEntry` 增红冲工厂（负金额构造）与 `reverse` 语义；`summaryOf` 汇总改为全条目净额代数和（PENDING earning + PENDING reversal）。
3. **服务层**：`SettlementApplicationService.reverseForRefund(order)`——按 `bookingId` 定位唯一 earning 条目；已存在红冲→幂等返回；生成负向条目（金额=原条目取负）。
4. **触发点两处**（与退款入账同事务）：`executeForBookingCancellation` 与 `executeArbitration(approve)`。
5. **文案修正**：仲裁 Preview「教师课酬结算条目作废」→「教师课酬将生成红冲条目（待结算净额相应扣减）」。
6. **提现前置**：`markPaidOut` 上线时必须纳入红冲净额；本整改建议作为提现功能上线的**前置任务**。
7. **存量数据订正（待确认）**：测试/演示库可能存在「已退款课时但条目仍 PENDING」的历史数据，需一次性订正脚本或运营工具（含幂等防重跑）。
8. **测试计划**：新增 IT——①取消已付订单后教师结算 summary 不含该课时；②同一订单重复触发红冲幂等（部分唯一索引兜底）；③仲裁退款红冲；④红冲与提现并发（复用 `OfflinePaymentConcurrencyIT` 的并发模式）。
9. **验收标准**：verify/E2E 全绿；教师结算视角「任何一笔退款后，pending 净额精确减少该课时酬劳」；审计日志含红冲动作。

### 9.45 历史账目只读清查结果（2026-10-10 下午，零数据修改）

| 类别 | 主库（nurture） | IT 库（nurture_test） |
|---|---|---|
| A. 退款 EXECUTED 仍挂正向 PENDING 条目 | **36 条，虚挂净额 ¥6,205**（35×¥170 + 1×¥255，全部来自「取消已付课时→自动退款」E2E 累计） | 0 |
| B. 条目 SETTLED/PAID_OUT 后订单退款 | 0（markSettled 零调用，状态不可达） | 0 |
| C. 已存在冲回记录 | 0（`entry_kind`/`reverses_entry_id` 列不存在，结构上不可能） | 同 |
| D. 退款/订单/结算状态不一致 | 0（EXECUTED 退款对应订单 100% CLOSED，级联完整） | 同 |

**存量订正待批要点**（对应 §9.5 第 3 项）：待核查数量=36 条；判定规则=`refund_cases.EXECUTED ⋈ settlement_entries` 正向条目；预期影响=红冲生成后教师待结算自动冲减 ¥6,205；备份=订正前 `settlement_entries` 全表快照 + pg_dump；回滚=按 `reverses_entry_id` 精确反查删除生成条目。

**另实证独立缺陷（不依赖红冲批准，建议单独排期修复）**：`summaryOf` 用 `PageRequest.of(0,50)` 汇总——主库教师 6fbeb796 现有 84 条 PENDING，DB 全量净额 ¥18,105 vs API 返回 ¥11,220（**少报 ¥6,885/38%**，entries 恰 50 条）。修复=全量聚合查询（明细仍可分页）+ 对未来负向红冲条目的代数和兼容。

### 9.5 待负责人确认（资金规则，未确认前不实施）

1. 选择方向一（作废）还是方向二（红冲，推荐）？
2. 部分退款场景教师侧是否按比例红冲（推荐）还是暂不支持部分退款？
3. 存量「已退款但未红冲」数据：订正脚本 / 运营工具 / 不订正？
4. 提现功能上线时间表——本整改是否列为其前置阻塞项？
