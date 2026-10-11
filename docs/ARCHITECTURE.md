# ARCHITECTURE — 真实架构速查（只描述当前存在，不做设计）

> 引用方：`PROJECT_HANDOFF.md`。目录基准：`线上家教平台（功能）/`。核实更新：2026-10-11 八阶段最终交付。

## 目录结构

```
backend/
  src/main/java/com/nurture/
    NurtureApplication.java
    shared/          # 横切：security(JWT/Cookie/CallerPrincipal/AccountStatusVerifier)
                     #        idempotency(强幂等过滤器) audit(审计) twophase(两阶段取消)
                     #        storage(文件) web(全局异常/业务异常) domain(AggregateRoot)
                     #        config scheduling(课程提醒@Scheduled) messaging? (在 messaging 模块)
    identity/ student/ parent/ teacher/ verification/ matching/ scheduling/
    booking/ classroom/ order/ payment/ refund/ settlement/ messaging/ review/ admin/
    learning/        # V9 新增：成长中心/课后任务/课堂记录/档案/答疑（原4实体/4仓库；V15新增计划/目标/关联/历史4实体4仓库及独立app/api；V16错题/附件/容量3实体3仓库及相应服务）
  src/main/resources/
    application.yml  # 唯一配置文件；生产经 env 注入（NURTURE_*）
    db/migration/    # Flyway V1–V21（V13试听限次、V14编号序列、V15学习计划、V16错题/文件/容量、V17重复档期、V18客服、V19小测、V20咨询附件、V21公开读取索引）
  src/test/java/com/nurture/
    arch/ArchitectureTest.java          # ArchUnit 1.5.1，5项全量严格规则/反例，无冻结豁免
    it/                                 # 147 IT 用例（真 PostgreSQL nurture_test 库，当前verify证据）
    ...                                 # 单元测试 44 项
frontend/
  src/app/
    core/api/endpoints.ts               # 全站 API 常量（API_BASE + learning/matching 新段）
    core/auth/ core/api/auth.interceptor.ts
    features/  landing/ auth/ teachers/ match/
               student/（dashboard/growth/archive/questions）
               parent/ teacher/ admin/ messages/ refunds/ support/ learning/（plans/mistakes/materials/assessments） scheduling/（series）
    shared/components/teacher-compare.component.ts      # 真实资料最多3人对比
    shared/components/teacher-profile-editor.component.ts
    shared/components/garden-scene/     # 雨后竹林场景层（呼吸感动效宿主）
    environments/                       # environment.production.ts 由 write-env.mjs 构建期注入
deploy/
  docker-compose{,.dev,.staging,.prod}.yml   # 四层叠加；staging 层=沙箱 true
  env.{staging,production}.example
  backup.sh restore.sh verify_config.py package-staging.sh RUNBOOK.md
  dist/                                  # Staging 部署包输出
tools/
  learning_materials_walkthrough.py learning_materials_migration_rehearsal.py
  e2e_test.py（本轮220通过） probe_authz.py（本轮19拒绝/0漏洞）
  gap_fill_walkthrough.py                # 实际API+桌面/375px浏览器
  settlement_correction_rehearsal.py legacy_migration_rehearsal.py legacy_refund_rehearsal.py
  sql/                                  # 只读对账与隔离必回滚订正演练
frontend/dist/nurture-frontend/browser/  # 生产构建产物（Pages 输出目录）
```

## 请求路径（真实）

本机：浏览器4200 → `/api`代理8080 → Spring Boot过滤器链（幂等 → 安全 → 审计）→ Controller（@PreAuthorize角色或SCOPE_）→ ApplicationService（业务 + 对象级授权）→ Repository → PostgreSQL（唯一事实源，源码Flyway V1–V21，ddl-auto=validate）。当前8080连接phase8隔离验收库V20，历史主库保持V10。

部署目标仍为Cloudflare Pages前端/Tunnel API，但完整Staging尚未建立。已发布的owner-private Sites只托管静态Angular，没有Java/PostgreSQL/API，顶部明确提示接口未连接。

## 模块间纪律（约定与实际守门范围）

- 业务模块间仅依赖对方app层，shared横切层按约定可访问；本模块app→infra允许，api不直连infra，domain不反向依赖app/api/infra。
- 当前五项ArchUnit检查全量执行并包含坏/好依赖反例，不使用FreezingArchRule。历史自定义noClasses条件方向错误已修正，admin直连OrderRepository与跨domain依赖已消除。原冻结文件只保留历史，不参与本轮规则。
- 跨模块订单/预约事实通过OrderSnapshot/BookingSnapshot只读记录；持有档期/打开会话返回标量；原交易转换、事务/悲观锁与无订单noRollbackFor保留。49单元/架构+147IT全过。


## 关键既有服务方法（新功能复用点）

- `BookingApplicationService`: `upcomingForStudent` / `ofStudent` / `todayLessonsForTeacher` / `studentOfOwnedBooking` / `hasTeachingRelation` / `studentsOfTeacher` / `isParticipant`
- `SchedulingApplicationService.slotOf(slotId)` → SlotView(OffsetDateTime start/end)
- `ParentApplicationService.isActiveGuardian(parent, student)` → 周报/授权闸门
- `MessagingApplicationService.notify(accountId, type, title, body)` → 站内通知
- `IdentityApplicationService.displayNameOf(accountId)`
- `SettlementApplicationService.reverseForRefund`：MANDATORY外层事务；追加负向条目、原条目锁与唯一约束，退款钱包/关单/审计一起回滚。
- `PlatformConfigurationReader`：admin的独立只读ConfigReader适配器，避免admin→booking→refund→settlement→ConfigReader循环。
- `TeacherRegistrationService`：身份注册与教师资料原子保存；ProfileView在事务内复制标签。
- `SmsDelivery`：短信投递端口；默认Unavailable实现，能力false/503，不伪造可用OTP。

公开教师端点只匿名允许白名单详情/列表，且要求账户有效、审核通过、在售；不包含联系方式或资质文件key；公开分页源码已改为matching.app PublicTeacherCatalog/infra参数化SQL只读投影，COUNT+当页两查询/数据库排序与分页；本机20k匹配条目已验证，生产持续负载尚未验证。资质下载只运营、固定kind、附件无缓存并记录读审计。咨询沿用数据库文本消息/私有附件+参与者权限、最新100/历史分页与已读；前端可见页面15秒轮询，未引入WebSocket或外部消息中间件。

## 测试拓扑

单元（*Test，surefire）→ IT（*IT + `it` profile，nurture_test 库，failsafe `-Pintegration`）→ E2E（tools/e2e_test.py，需服务运行）→ 探针（probe_authz.py）→ 配置门禁（deploy/verify_config.py）。

## V18–V20补充与工程契约

admin模块的客服工单/回复/证据/容量支持四角色归属、版本行锁、审计/站内通知，无自动退款。learning新增测评/尝试两实体，题目存验证JSON，有限客观题/一次评分/教师批注/真实报告；messaging新增咨询文件/容量，与消息同事务记录attachment_id。文件类型/内容验证与受控下载复用shared存储，逻辑保留配额，病毒扫描/物理自动清理尚未提供。

学生历史钱包只读接口经parent.app，按账户→既有parent_profile→wallet查询，不ensure创建、不转移。幂等过滤器要求规范36字符UUIDv4/RFC variant；合法大小写均接受。auth/landing/match品牌装饰按组件标签限定到styles/brand-presentation.css，声明保留、原预算零warning。

## 续作1查询读取边界

PublicTeacherCatalog应用端口返回限定公开Row；matching.infra JDBC只读投影跨单体中公开账户、审核、师资、评价和档期表，禁止跨模块写入与foreign domain/infra类依赖。同一REPEATABLE_READ快照与now确保页/COUNT一致，最新审核submitted_at/id稳定。V21新增四索引，49单元/架构+13专项IT通过，全套IT待三项后总回归；8080仍phase8/V20，8081仅loopback性能库V21，见PROJECT_HANDOFF §4。
