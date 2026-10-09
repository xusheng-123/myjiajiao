# ARCHITECTURE — 真实架构速查（只描述当前存在，不做设计）

> 引用方：`PROJECT_HANDOFF.md`。目录基准：`线上家教平台（功能）/`

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
    learning/        # V9 新增：成长中心/课后任务/课堂记录/档案/答疑（domain 4 实体 + infra 4 仓库 + app 服务 + api 控制器）
  src/main/resources/
    application.yml  # 唯一配置文件；生产经 env 注入（NURTURE_*）
    db/migration/    # Flyway V1–V10（V9=learning_growth，V10=favorite 补列）
  src/test/java/com/nurture/
    arch/ArchitectureTest.java          # ArchUnit 1.5.1，FreezingArchRule 空基线
    it/                                 # 8 个 IT 类（真 PostgreSQL nurture_test 库）
    ...                                 # 单元测试 38 项
frontend/
  src/app/
    core/api/endpoints.ts               # 全站 API 常量（API_BASE + learning/matching 新段）
    core/auth/ core/api/auth.interceptor.ts
    features/  landing/ auth/ teachers/ match/
               student/（dashboard/growth/archive/questions）
               parent/ teacher/ admin/
    shared/components/garden-scene/     # 雨后竹林场景层（呼吸感动效宿主）
    environments/                       # environment.production.ts 由 write-env.mjs 构建期注入
deploy/
  docker-compose{,.dev,.staging,.prod}.yml   # 四层叠加；staging 层=沙箱 true
  env.{staging,production}.example
  backup.sh restore.sh verify_config.py package-staging.sh RUNBOOK.md
  dist/                                  # Staging 部署包输出
tools/
  e2e_test.py（114 项） probe_authz.py（越权探针） verify_config.py 由 deploy/ 管
frontend/dist/nurture-frontend/browser/  # 生产构建产物（Pages 输出目录）
```

## 请求路径（真实）

浏览器/AI Agent → Cloudflare（Pages=前端 / Tunnel=api 域名 → 容器网 backend:8080）→ Spring Boot 过滤器链（幂等 → 安全 → 审计）→ Controller（@PreAuthorize 角色或 SCOPE_）→ ApplicationService（业务 + 对象级授权）→ Repository → PostgreSQL（唯一事实源，Flyway V1–V10，ddl-auto=validate）。

## 模块间纪律（ArchUnit 强制）

- 模块 A 只准 import 模块 B 的 `app` 层；**infra 禁止跨模块**；控制器零仓储依赖。
- 当前违规基线 = 0。learning/parent(LearningGateway) 均遵守（parent 经 gateway 调 learning.app）。

## 关键既有服务方法（新功能复用点）

- `BookingApplicationService`: `upcomingForStudent` / `ofStudent` / `todayLessonsForTeacher` / `studentOfOwnedBooking` / `hasTeachingRelation` / `studentsOfTeacher` / `isParticipant`
- `SchedulingApplicationService.slotOf(slotId)` → SlotView(OffsetDateTime start/end)
- `ParentApplicationService.isActiveGuardian(parent, student)` → 周报/授权闸门
- `MessagingApplicationService.notify(accountId, type, title, body)` → 站内通知
- `IdentityApplicationService.displayNameOf(accountId)`

## 测试拓扑

单元（*Test，surefire）→ IT（*IT + `it` profile，nurture_test 库，failsafe `-Pintegration`）→ E2E（tools/e2e_test.py，需服务运行）→ 探针（probe_authz.py）→ 配置门禁（deploy/verify_config.py）。
