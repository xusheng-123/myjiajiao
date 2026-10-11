# 润物细无声 (NURTURE) · 前端架构与全站 UI/UX 摸底审计报告

> **报告版本**：2026-10-11 最终审计版  
> **执行角色**：Agent 01 项目侦查与架构代理  
> **目标工程**：`frontend/` (Angular 19, TypeScript, esbuild application builder)  
> **审计原则**：只读摸底、以代码事实为准、不篡改源码、吸收既有交付成果。  
> **交付物归档路径**：`docs/ui-redesign-2026-10-11/audit/frontend-audit.md`

---

## 一、审计概述与现场环境

本报告为全站 UI/UX 重构前的系统摸底，全面扫描了 `frontend/src/` 下的 20 个路由页面、10 个共享组件、样式系统、鉴权链路、状态管理以及与后端 29 个 Controller（约 150 个 REST 端点）的实际调用接缝。

### 1.1 运行现场与基线核实
- **前端技术栈**：Angular 19.2.0 + TypeScript 5.7.2 + RxJS 7.8.0 + esbuild application builder (`@angular-devkit/build-angular:application`)。
- **环境配置**：`proxy.conf.json` 代理 `/api` 到 `127.0.0.1:8080`；CORS 白名单默认 `http://localhost:4200`（禁止使用 `127.0.0.1:4200` 登录，否则触发 403 Invalid CORS request）。
- **硬约束基线**：
  - 严禁动微服务、严禁引入外部大模型 API / 自研 RTC / 重型状态库；
  - 严禁改动已有核心交易资金结算与订单流转逻辑；
  - 品牌视觉锁定：`tools/verify_brand_css.cjs` 严格监控 1,065 条品牌 CSS 声明，任何改动必须保持集合等价。

---

## 二、路由全清单（Routing Architecture）

全站路由集中定义于 `frontend/src/app/app.routes.ts`，未分散在 feature 模块内部。共有 **20 个独立页面路由**、4 个父级分组重定向路由、1 个通配符回退。

### 2.1 路由总表

| 序号 | 路由路径 (`path`) | 加载方式 | 守卫 (`canActivate`) | 角色/权限要求 | 目标组件 (`Component`) |
|:---:|---|---|---|---|---|
| 1 | `''` (根路径) | `loadComponent` | 无（公开） | 任意访客 | `LandingComponent` |
| 2 | `teachers` | `loadComponent` | 无（公开） | 任意访客 | `TeachersComponent` |
| 3 | `teachers/:teacherId` | `loadComponent` | 无（公开） | 任意访客 | `TeacherDetailComponent` |
| 4 | `auth` | `loadComponent` | 无（公开） | 任意访客 | `AuthPortalComponent` |
| 5 | `student` | `children` | `roleGuard(Role.STUDENT)` | 学生 (`STUDENT`) | 重定向至 `student/dashboard` |
| 6 | `student/dashboard` | `loadComponent` | 继承父级守卫 | 学生 (`STUDENT`) | `StudentDashboardComponent` |
| 7 | `student/match` | `loadComponent` | 继承父级守卫 | 学生 (`STUDENT`) | `MatchComponent` |
| 8 | `student/growth` | `loadComponent` | 继承父级守卫 | 学生 (`STUDENT`) | `StudentGrowthComponent` |
| 9 | `student/archive` | `loadComponent` | 继承父级守卫 | 学生 (`STUDENT`) | `StudentArchiveComponent` |
| 10 | `student/questions` | `loadComponent` | 继承父级守卫 | 学生 (`STUDENT`) | `StudentQuestionsComponent` |
| 11 | `parent` | `children` | `roleGuard(Role.PARENT)` | 家长 (`PARENT`) | 重定向至 `parent/dashboard` |
| 12 | `parent/dashboard` | `loadComponent` | 继承父级守卫 | 家长 (`PARENT`) | `ParentDashboardComponent` |
| 13 | `teacher` | `children` | `roleGuard(Role.TEACHER)` | 导师 (`TEACHER`) | 重定向至 `teacher/dashboard` |
| 14 | `teacher/dashboard` | `loadComponent` | 继承父级守卫 | 导师 (`TEACHER`) | `TeacherDashboardComponent` |
| 15 | `admin` | `children` | `roleGuard(Role.ADMIN)` | 运营 (`ADMIN`) | 重定向至 `admin/dashboard` |
| 16 | `admin/dashboard` | `loadComponent` | 继承父级守卫 | 运营 (`ADMIN`) | `AdminDashboardComponent` |
| 17 | `learning/plans` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `LearningPlansComponent` |
| 18 | `learning/materials` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `LearningMaterialsComponent` |
| 19 | `learning/mistakes` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `MistakeNotesComponent` |
| 20 | `learning/assessments` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `AssessmentsComponent` |
| 21 | `messages` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `MessagesComponent` |
| 22 | `support` | `loadComponent` | `sessionGuard` | 已登录任意角色 | `SupportCasesComponent` |
| 23 | `refunds` | `loadComponent` | `refundGuard` | `STUDENT` 或 `PARENT` | `RefundsComponent` |
| 24 | `scheduling/series` | `loadComponent` | `roleGuard(Role.TEACHER)` | 导师 (`TEACHER`) | `RecurringAvailabilityComponent` |
| 25 | `**` | 重定向 | 无 | 任意访客 | 重定向至 `''` |

### 2.2 路由守卫机制分析 (`frontend/src/app/core/auth/auth.guard.ts`)
1. **`sessionGuard`**：检查 `auth.isLoggedIn()`，未登录时通过 `createUrlTree(['/auth'])` 重定向至登录门户。
2. **`roleGuard(requiredRole)`**：校验当前登录会话中的角色 `auth.role() === required`。未匹配时重定向至 `/auth?role=<required>`，将目标角色带入查询参数，便于登录门户自动切换对应角色 Tab。
3. **`refundGuard`**：复合守卫，要求必须已登录且角色为 `STUDENT` 或 `PARENT`，教师与运营访问会被重定向至 `/auth`。

---

## 三、页面清单与逐页档案（Page Inventory & Deep Profiling）

本节对全站全部 20 个页面进行逐页深度剖析。

### 1. 营销首页 (LandingPage)
- **URL**：`/`
- **组件文件路径**：`frontend/src/app/features/landing/landing.component.ts` (配合 `landing.component.html`, `landing.component.css`, `landing-data.ts`)
- **引用的共享组件**：`<nur-garden-scene>`, `<nur-teacher-card>`
- **业务目标**：品牌人文理念传达、师资亮点透传、核心服务逻辑展示、引流转化。
- **主要用户角色**：未登录访客、潜在学生与家长。
- **核心交互**：首屏呼吸感入场、精选导师卡片悬停预览与直接点击预约/咨询跳转、多维度价值主张滚动走查。
- **依赖的 API 服务/端点**：`TeachersApi.search`（配置 `MockTeachersApiService` 或正式 `/matching/public/teachers`）。
- **UI 问题**：页面垂直滚动过长（209 行 HTML + 170 行静态数据），底部页脚与首屏信息密度悬殊；过度依赖静态伪数据（虽已注明免责，但缺乏动态数据联动）。
- **ADHD 体验风险**：视觉层级繁多容易导致认知漂移；首屏 CTA 虽显著，但中段「学习理念」和「信任保障」文字过密，缺乏视线呼吸停顿点。
- **性能风险**：渲染 4 处 `<nur-garden-scene>` 变体（stems, leaves, ripples），CSS 关键帧竹林微风动画常驻消耗 GPU。
- **重构优先级**：**P2**（营销展示页，非交易闭环缺口）。

### 2. 导师公开检索与筛选页 (TeachersCatalogPage)
- **URL**：`/teachers`
- **组件文件路径**：`frontend/src/app/features/teachers/teachers.component.ts` (配合 `teachers.component.html`, `teachers.component.css`)
- **引用的共享组件**：`<nur-teacher-card>`, `<nur-teacher-compare>`
- **业务目标**：提供全量师资的公开浏览、多维筛选、价格排序与横向比选。
- **主要用户角色**：访客、学生、家长。
- **核心交互**：自由关键词输入、科目/学段/风格标签点选、单价区间滑块/输入、排序切换（价格升降、评分、评价数）、最多 3 人对比抽屉勾选与横向对比、分页翻页。
- **依赖的 API 服务/端点**：`TeachersApi.searchPage` (`GET /api/v1/matching/public/teachers-page`)。
- **UI 问题**：筛选器区域高度在移动端挤占超过半屏；空状态仅有一句简陋文字；对比抽屉缺乏吸底悬浮感。
- **ADHD 体验风险**：过滤条件众多容易引发「选择瘫痪（Choice Paralysis）」；重置全部条件时无明确过渡反馈。
- **性能风险**：单页 20 条复杂卡片重渲染，卡片内部存在 sheen 动效。
- **重构优先级**：**P1**（高频找老师枢纽，直接影响平台转化率）。

### 3. 导师公开详情页 (TeacherDetailPage)
- **URL**：`/teachers/:teacherId`
- **组件文件路径**：`frontend/src/app/features/teachers/teacher-detail.component.ts`
- **引用的共享组件**：无
- **业务目标**：全方位展示导师公开背景、在售课程、可约时段，促成课前咨询与课程预约。
- **主要用户角色**：学生、家长、访客。
- **核心交互**：查看资料、点击「课前咨询」创建会话并跳转 `/messages`、点击课程「选择时间并预约」跳转 `/student/match`。
- **依赖的 API 服务/端点**：`GET /api/v1/matching/public/teachers/{id}`, `POST /api/v1/messaging/conversations`。
- **UI 问题**：**严重裸文字与缺乏信息结构**！内联 template，无头像，评分只有一行纯文本汇总（`学员评分 5 · 38 条评价`），**完全没有评价列表区（缺口 C1）**；课程与档期全为纯原生 `<ul><li>`，无卡片呼吸感。
- **ADHD 体验风险**：单调冗长的文字流极易造成阅读疲劳，关键转化按钮（咨询/预约）埋在文字列表后，缺乏引导动线。
- **性能风险**：极低（无重型动画），但数据加载期仅有裸文字占位，缺乏骨架屏。
- **重构优先级**：**P1**（严重影响交易转化的核心信息页，急需补齐评价列表 C1 与卡片化布局）。

### 4. 统一鉴权门户 (AuthPortalPage)
- **URL**：`/auth`
- **组件文件路径**：`frontend/src/app/features/auth/auth-portal.component.ts` (配合 `auth-portal.component.html`, `auth-portal.component.css`)
- **引用的共享组件**：`<nur-garden-scene>`
- **业务目标**：四角色身份统一登录、短信/密码双通道鉴权、新用户注册与教师入驻初审资料提交。
- **主要用户角色**：学生、家长、导师、运营。
- **核心交互**：角色 Tab 切换、通道切换、表单就地验证、倒计时获取验证码、就地错误容错（网络中断保留输入）、提交后角色驱动分流。
- **依赖的 API 服务/端点**：`GET /api/v1/identity/auth/capabilities`, `POST /api/v1/identity/auth/sms-codes`, `POST /api/v1/identity/auth/login:password`, `POST /api/v1/identity/auth/login:sms`, `POST /api/v1/identity/auth/register:*`。
- **UI 问题**：受限于 `verify_brand_css.cjs` 严格锁定了 1,065 条声明；表单部分次级辅助文字在暗色背景下对比度较低。
- **ADHD 体验风险**：表单容错与保留状态机制优秀（无刺眼红框、保留输入），但四角色 Tab 并列可能让首次访问的学生/家长产生选择犹疑。
- **性能风险**：CSS 声明巨大，包含 SVG 月洞门与雨丝动效，HTML 264 行。
- **重构优先级**：**P2**（现有交互完整且有品牌锁保护，改动成本与风险高）。

### 5. 学生工作台 (StudentDashboardPage)
- **URL**：`/student/dashboard`
- **组件文件路径**：`frontend/src/app/features/student/student-dashboard.component.ts` (配合 `student-dashboard.component.html`, `student-dashboard.component.css`)
- **引用的共享组件**：`<nur-calendar-export>`, `<nur-notice-center>`
- **业务目标**：学生日常核心控制台，推进课前准备、进入课堂履约、管理监护绑定、查看课表与改期。
- **主要用户角色**：学生。
- **核心交互**：一键专注模式切换（隐藏次要区域）、课前任务勾选（无序切块打勾）、点击进入课堂（拉取会议链接并弹窗）、监护绑定确认、课表改期申请。
- **依赖的 API 服务/端点**：`GET /api/v1/student/me/focus-overview`, `GET /api/v1/student/me/upcoming-lessons`, `GET /api/v1/parent/me/children:pending`, `POST /api/v1/parent/me/children:confirm`, `POST /api/v1/student/me/preparation-tasks/{id}:complete`, `POST /api/v1/booking/bookings/{id}:reschedule`, `GET /api/v1/classroom/bookings/{id}/join`, `GET /api/v1/order/me/orders`。
- **UI 问题**：顶部散落 4 组裸文字超链接（`<nav><a ...></a></nav>`）；**严重缺失：取消预约入口（缺口 A1）、我的预约列表/历史列表（缺口 A3）、学生个人资料编辑（缺口 B2）、学习目标画像卡（缺口 B1）**；内联改期弹窗导致布局上下推挤。
- **ADHD 体验风险**：非专注模式下卡片垂直堆叠过多；次级任务与链接缺乏视觉分组，容易打断学习心流。
- **性能风险**：大组件（TS 390 行, HTML 249 行），维护 12 个局部 signals，页面轮询与倒计时较多。
- **重构优先级**：**P0**（核心交易闭环与日常高频总枢纽，需补齐 A1/A3/B1/B2）。

### 6. 需求发布与精准匹配页 (StudentMatchPage)
- **URL**：`/student/match`
- **组件文件路径**：`frontend/src/app/features/match/match.component.ts` (配合 `match.component.html`, `match.component.css`)
- **引用的共享组件**：`<nur-teacher-compare>`
- **业务目标**：发布学习需求、计算导师规则化推荐、发起试听或正式课预约。
- **主要用户角色**：学生、家长。
- **核心交互**：科目/学段/预算/风格需求表单录入、提交匹配拉取推荐结果、导师档期拉取与日历选点、提交预约表单（试听/正式课）、收藏切换、最多 3 人比选。
- **依赖的 API 服务/端点**：`POST /api/v1/matching/demands`, `GET /api/v1/matching/teachers:recommend`, `GET /api/v1/scheduling/teachers/{id}/open-slots`, `POST /api/v1/booking/bookings`, `POST /api/v1/messaging/conversations`, `POST /api/v1/matching/teachers/{id}:favorite`。
- **UI 问题**：**全站第二大组件**（TS 527 行, HTML 326 行）；需求收集、推荐列表、档期选择弹窗全部揉在单一组件内；档期点选按钮极其紧密。
- **ADHD 体验风险**：表单字段过多，从发布需求到预约确认的路径缺乏步进式进度感知（Wizard），容易半途弃填。
- **性能风险**：状态机复杂（FORM / RESULT / SLOTS / BOOKING），DOM 结构深且大。
- **重构优先级**：**P0**（发起交易预约的唯一入口，闭环核心）。

### 7. 学习成长中心 (StudentGrowthPage)
- **URL**：`/student/growth`
- **组件文件路径**：`frontend/src/app/features/student/student-growth.component.ts`
- **引用的共享组件**：`<nur-notice-center>`
- **业务目标**：呈现下一节课提醒、今日待办任务、本周学时与任务统计、连续学习打卡天数与动态日志。
- **主要用户角色**：学生。
- **核心交互**：查看动态、点击跳转下一节课程、浏览连续打卡天数。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/growth`。
- **UI 问题**：内联 template；统计指标硬编码内联 `style="font-size:1.6rem;font-weight:700"`；缺乏图表与环形进度条等现代数据可视化表达。
- **ADHD 体验风险**：纯文字数字展示缺乏情绪价值与正向激励反馈，难以让 ADHD 学员获得即时多巴胺成就感。
- **性能风险**：极低。
- **重构优先级**：**P1**（成长陪伴关键感知页，需提升卡片美学与可视化）。

### 8. 学习档案页 (StudentArchivePage)
- **URL**：`/student/archive`
- **组件文件路径**：`frontend/src/app/features/student/student-archive.component.ts`
- **引用的共享组件**：`<nur-notice-center>`
- **业务目标**：汇总沉淀学生的长期学习目标、历史授课教师记录、统计总览与教学履约历史。
- **主要用户角色**：学生、授课教师（待补入口）。
- **核心交互**：只读浏览、退出登录。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/archive`。
- **UI 问题**：内联 template 与大量行内 `style="..."`；教师课堂记录仅为一条左侧边框的彩色方块；排版极度扁平。
- **ADHD 体验风险**：文字密度过高且无时间线轴（Timeline），缺乏阶段成果回顾的仪式感。
- **性能风险**：极低。
- **重构优先级**：**P1**（学情沉淀载体，需引入时间轴与卡片结构）。

### 9. 课后答疑页 (StudentQuestionsPage)
- **URL**：`/student/questions`
- **组件文件路径**：`frontend/src/app/features/student/student-questions.component.ts`
- **引用的共享组件**：`<nur-notice-center>`, `<nur-learning-attachments>`
- **业务目标**：学生针对具体授课教师发起异步答疑、上传附件、查看教师回复。
- **主要用户角色**：学生。
- **核心交互**：选择授课教师、输入问题科目/标题/内容、提交提问、查看历史回答、查看附件。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/questions`, `POST /api/v1/learning/questions`, `GET /api/v1/learning/files/teachers`。
- **UI 问题**：内联 template；**严重缺失：关闭答疑按钮（缺口 B3）**，导致提问解决后状态永远停留在 `ANSWERED` 无法关闭；提问表单与历史记录同屏，没有主次拆分。
- **ADHD 体验风险**：输入框无本地草稿自动保存，意外跳出即丢失；历史列表缺乏按状态筛选（待答/已答/已关闭）。
- **性能风险**：极低。
- **重构优先级**：**P1**（需补齐 B3 关闭操作并优化表单与列表交互）。

### 10. 家长工作台 (ParentDashboardPage)
- **URL**：`/parent/dashboard`
- **组件文件路径**：`frontend/src/app/features/parent/parent-dashboard.component.ts` (配合 `parent-dashboard.component.html`, `parent-dashboard.component.css`)
- **引用的共享组件**：`<nur-calendar-export>`, `<nur-notice-center>`
- **业务目标**：家长管理子女监护绑定、代付下单与支付、钱包充值、周报查看、日程导出。
- **主要用户角色**：家长。
- **核心交互**：输入学生号发起绑定、查看绑定状态、代付下单（查看可下单课程、确认订单、拉取支付配置、发起支付）、钱包充值、展开子女学习周报面板。
- **依赖的 API 服务/端点**：`GET /api/v1/parent/me/children`, `POST /api/v1/parent/me/children:bind`, `GET /api/v1/order/bookings:orderable`, `POST /api/v1/order/orders`, `GET /api/v1/payment/config`, `POST /api/v1/payment/payments:initiate`, `POST /api/v1/parent/me/wallet:recharge`, `GET /api/v1/parent/me/weekly-report`。
- **UI 问题**：子女卡片右侧拥挤堆叠 6 个超链接与按钮（学习计划、小测报告、错题、资料、日历导出、学习周报）；**缺失：取消预约入口（缺口 A1）**；支付流程与钱包状态缺乏步骤指引。
- **ADHD 体验风险**：金钱交易涉及多个状态（待支付、支付中、已支付、余额不足），状态变化缺乏清晰的双通道（图标+文字）大卡片指引。
- **性能风险**：TS 290 行, HTML 246 行，包含复杂的支付状态机。
- **重构优先级**：**P0**（核心交易与支付履约枢纽）。

### 11. 导师工作台 (TeacherDashboardPage)
- **URL**：`/teacher/dashboard`
- **组件文件路径**：`frontend/src/app/features/teacher/teacher-dashboard.component.ts` (配合 `teacher-dashboard.component.html`, `teacher-dashboard.component.css`)
- **引用的共享组件**：`<nur-teacher-profile-editor>`, `<nur-notice-center>`
- **业务目标**：导师日常教学履约、预约审批、排课日历、课堂准备、资质提交、科目定价与结算查看。
- **主要用户角色**：导师。
- **核心交互**：今日课表查看、改期处理、课堂准备（登记外部会议链接、进入课堂、结课、考勤）、预约申请审批（接受/拒绝）、排课矩阵点击发布、资质双文件上传、科目添加/下架/调价、结算对账。
- **依赖的 API 服务/端点**：`GET /api/v1/teacher/me/today-lessons`, `GET /api/v1/teacher/me/booking-requests`, `POST /api/v1/teacher/me/booking-requests/{id}:decide`, `GET /api/v1/scheduling/me/calendar`, `POST /api/v1/scheduling/availability-slots`, `GET /api/v1/verification/me/status`, `POST /api/v1/verification/cases`, `GET /api/v1/teacher/me/subjects`, `POST /api/v1/teacher/me/subjects`, `PUT /api/v1/teacher/me/subjects/{id}/pricing`, `DELETE /teacher/me/subjects/{id}`, `GET /api/v1/teacher/me/settlement`, `POST /api/v1/classroom/bookings/{id}/session`, `POST /api/v1/classroom/bookings/{id}/session:end`, `POST /api/v1/classroom/bookings/{id}/attendance`。
- **UI 问题**：**全站第一超级巨无霸组件**（TS 612 行, HTML 392 行）！垂直平铺了 8 个庞大业务卡片；**严重缺失核心闭环功能：开始上课/完成课程（缺口 A2）、取消预约（缺口 A1）、教师课堂记录录入（缺口 B4）、我的学生列表（缺口 B5）、收到的评价（缺口 C2）、课前任务生成（缺口 B6）**！
- **ADHD 体验风险**：信息严重过载！导师进入工作台后面临排山倒海的信息轰炸，完全缺乏今日行动聚焦点。
- **性能风险**：**极高**。代码量超 1,000 行，包含日历网格计算、多模态框状态与频繁的本地 signal 更新。
- **重构优先级**：**P0**（全站重构第一痛点，必须拆分组件并补齐闭环缺口）。

### 12. 运营治理控制台 (AdminDashboardPage)
- **URL**：`/admin/dashboard`
- **组件文件路径**：`frontend/src/app/features/admin/admin-dashboard.component.ts` (配合 `admin-dashboard.component.html`, `admin-dashboard.component.css`)
- **引用的共享组件**：`<nur-queue-pager>`, `<nur-notice-center>`
- **业务目标**：平台全景数据大盘、五大治理队列（资质审核、退款仲裁、账户管理、审计日志、线下待支付订单确认）。
- **主要用户角色**：运营管理员。
- **核心交互**：平台指标查阅、资质审核与受控附件下载、退款仲裁预览与两阶段执行、账户封禁与解封、线下付款核验确认、各队列状态过滤与翻页。
- **依赖的 API 服务/端点**：`GET /api/v1/admin/overview`, `GET /api/v1/admin/verification-cases`, `POST /api/v1/admin/verification-cases/{id}:decide`, `GET /api/v1/verification/cases/{id}/materials/{key}`, `GET /api/v1/admin/refund-cases`, `POST /api/v1/admin/refund-cases/{id}/arbitration:preview`, `POST /api/v1/admin/refund-cases/{id}/arbitration:execute`, `GET /api/v1/admin/accounts`, `POST /api/v1/admin/accounts/{id}:lock`, `POST /api/v1/admin/accounts/{id}:unlock`, `GET /api/v1/admin/audit-logs`, `GET /api/v1/admin/orders/pending-payment`, `POST /api/v1/admin/orders/{id}:confirm-offline-payment`。
- **UI 问题**：五大队列直接垂直排布到底，页面长度超 5 屏；**缺失：平台全局参数配置（缺口 D1）、登录限流解锁（缺口 D2）**。
- **ADHD 体验风险**：队列之间缺乏 Tab 切换或侧边工作台抽屉，审核时需反复上下滚动寻找操作目标。
- **性能风险**：TS 248 行, HTML 218 行，同时维护 5 组独立分页状态。
- **重构优先级**：**P1**（高频内部治理台，需重构为 Tab 切换结构并补齐 D1/D2）。

### 13. 退款与处理进度页 (RefundsPage)
- **URL**：`/refunds`
- **组件文件路径**：`frontend/src/app/features/refunds/refunds.component.ts` (配合 `refunds.component.html`, `refunds.component.css`)
- **引用的共享组件**：无
- **业务目标**：展示可退款订单、发起全额退款申请、查看退款仲裁处理进度与运营批注。
- **主要用户角色**：学生、家长。
- **核心交互**：查看订单列表、点击「申请退款」内联展开理由表单、提交申请、查看我的退款工单进度、翻页。
- **依赖的 API 服务/端点**：`GET /api/v1/order/me/refundable-orders`, `POST /api/v1/refund/refund-cases`, `GET /api/v1/refund/refund-cases/mine`。
- **UI 问题**：界面极其简陋；订单与表单直接内联在列表项中，没有模态框或向导；历史退款为纯文本列表。
- **ADHD 体验风险**：缺乏退款进度时间轴（提交→审核中→已退款到钱包/已驳回），用户对资金去向容易产生焦虑。
- **性能风险**：极低（代码仅 63 行）。
- **重构优先级**：**P1**（资金安全与信任保障凭据页，需引入 Timeline 进度表达）。

### 14. 课前咨询与即时会话页 (MessagesPage)
- **URL**：`/messages`
- **组件文件路径**：`frontend/src/app/features/messages/messages.component.ts` (配合 `messages.component.html`, `messages.component.css`)
- **引用的共享组件**：`<nur-storage-quota>`
- **业务目标**：学生/家长与导师课前沟通意向、确定课时要求、互发课件附件、查看已读未读状态。
- **主要用户角色**：学生、家长、导师。
- **核心交互**：会话列表选择、消息气泡流浏览、15 秒自动轮询拉取新消息、加载更早历史消息、发送纯文本消息、上传与下载受控附件、配额刷新。
- **依赖的 API 服务/端点**：`GET /api/v1/messaging/conversations`, `GET /api/v1/messaging/conversations/{id}/messages`, `POST /api/v1/messaging/conversations/{id}/messages`, `POST /api/v1/messaging/files/upload`, `GET /api/v1/messaging/files/{id}/download`, `GET /api/v1/messaging/files/quota`。
- **UI 问题**：移动端响应式直接从 280px 双栏断裂为单列纵向排列，缺乏移动端会话列表与聊天窗的平滑推入返回；消息气泡缺少头像与发送失败重试机制。
- **ADHD 体验风险**：15 秒静默轮询更新缺乏视觉通知提示；消息输入区缺乏草稿自动暂存。
- **性能风险**：消息列表无虚拟滚动，会话消息过多时 DOM 节点持续累积；轮询定时器常驻。
- **重构优先级**：**P1**（沟通转化核心高频工具）。

### 15. 客服与申诉工单页 (SupportCasesPage)
- **URL**：`/support`
- **组件文件路径**：`frontend/src/app/features/support/support-cases.component.ts`
- **引用的共享组件**：`<nur-queue-pager>`, `<nur-storage-quota>`
- **业务目标**：用户向运营提交诉求/申诉、关联特定课程、提交私有证据附件、运营回复与状态推进。
- **主要用户角色**：全角色（学生、家长、导师、运营）。
- **核心交互**：提交工单（选分类、选课程、输入标题描述）、工单分页筛选、工单详情查阅、上传/下载/移除私有证据、追加回复、运营推进状态。
- **依赖的 API 服务/端点**：`GET /api/v1/support/cases/course-options`, `GET /api/v1/support/cases`, `POST /api/v1/support/cases`, `GET /api/v1/support/cases/{id}`, `GET /api/v1/support/cases/{id}/replies`, `POST /api/v1/support/cases/{id}/replies`, `POST /api/v1/support/cases/{id}/status`, `POST /api/v1/support/cases/{id}/evidence`, `GET /api/v1/support/cases/evidence/{id}/download`, `DELETE /api/v1/support/cases/evidence/{id}`, `GET /api/v1/support/cases/quota`。
- **UI 问题**：**内联 template 与内联 styles**；表单、工单列表、工单详情、证据附件、沟通流全部纵向堆积在单一页面中，排版粗糙。
- **ADHD 体验风险**：信息过于庞杂（课程选择器+分页、工单列表+分页、证据列表+配额、沟通记录+分页、回复表单、状态流转），极易引发认知过载。
- **性能风险**：组件逻辑紧凑，维护了三层并发版本号守卫（listVersion, detailVersion, historyVersion, courseVersion）。
- **重构优先级**：**P1**（全平台售后纠纷保障通道，需优化为 Master-Detail 结构）。

### 16. 重复档期与日历发布页 (RecurringAvailabilityPage)
- **URL**：`/scheduling/series`
- **组件文件路径**：`frontend/src/app/features/scheduling/recurring-availability.component.ts` (配合 `recurring-availability.component.html`)
- **引用的共享组件**：`<nur-calendar-export>`
- **业务目标**：导师按周批量编排未来档期、逐次排查冲突、设置例外跳过、发布档期并导出 ICS。
- **主要用户角色**：导师。
- **核心交互**：录入规则表单（日期、时间、时长、周数）、点击「预览逐次冲突」、逐周勾选跳过或修改时段、一键批量发布、查看已发布规则与单档期下架。
- **依赖的 API 服务/端点**：`POST /api/v1/scheduling/series:inspect`, `POST /api/v1/scheduling/series:publish`, `GET /api/v1/scheduling/series`, `GET /api/v1/scheduling/series/{id}`, `DELETE /api/v1/scheduling/availability-slots/{id}`。
- **UI 问题**：完全缺乏图形化的日历周视图（Week Calendar View），逐次预览全为纯文本条目；没有可视化的时间冲突标记。
- **ADHD 体验风险**：时间与冲突全靠文字识别，无法在脑海中快速建立时间块（Time-blocking）空间感知。
- **性能风险**：低。
- **重构优先级**：**P2**（导师高级工具，业务逻辑完善，需补足可视化日历表现）。

### 17. 学习计划与阶段目标页 (LearningPlansPage)
- **URL**：`/learning/plans`
- **组件文件路径**：`frontend/src/app/features/learning/learning-plans.component.ts` (配合 `learning-plans.component.html`, `learning-plans.component.css`)
- **引用的共享组件**：`<nur-learning-attachments>`
- **业务目标**：师生共同制定长期计划、拆解阶段里程碑、绑定课后任务、追踪真实完成进度并溯源修改日志。
- **主要用户角色**：学生、导师、家长（家长只读）。
- **核心交互**：计划建立与调整、里程碑达成切换、关联已有任务、发布并关联新任务、查看修改审计流水。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/plans`, `POST /api/v1/learning/plans`, `PUT /api/v1/learning/plans/{id}`, `POST /api/v1/learning/plans/{id}/archive`, `POST /api/v1/learning/plans/{id}/milestones`, `POST /api/v1/learning/plans/{id}/tasks:link`, `DELETE /api/v1/learning/plans/{id}/tasks/{taskId}`, `GET /api/v1/learning/plans/{id}/history`, `GET /api/v1/learning/plans/students`, `GET /api/v1/learning/plans/task-options`, `GET /api/v1/learning/plans/eligible-lessons`, `POST /api/v1/learning/teacher/tasks`。
- **UI 问题**：单卡片内嵌套了里程碑、关联任务、管理面板、修改历史等 4 层子视图；原生按钮密集且未分主次。
- **ADHD 体验风险**：嵌套折叠面板展开时页面大幅跳动，容易造成视线迷失。
- **性能风险**：中等，卡片内多层循环渲染。
- **重构优先级**：**P1**（学习过程管理核心功能，需理顺层级）。

### 18. 教学资料与作业管理页 (LearningMaterialsPage)
- **URL**：`/learning/materials`
- **组件文件路径**：`frontend/src/app/features/learning/learning-materials.component.ts` (配合 `learning-materials.component.html`)
- **引用的共享组件**：`<nur-storage-quota>`, `<nur-learning-attachments>`
- **业务目标**：课件课后资料存取、课后作业发布与提交批改、课后答疑回复。
- **主要用户角色**：学生、导师、家长（家长只读）。
- **核心交互**：关联课程上传课件、下载/移除课件、填写提交作业、教师填写批改反馈、导师回答答疑问题。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/files/quota`, `GET /api/v1/learning/files`, `POST /api/v1/learning/files/upload`, `GET /api/v1/learning/files/{id}/download`, `DELETE /api/v1/learning/files/{id}`, `GET /api/v1/learning/tasks`, `POST /api/v1/learning/tasks/{id}:submit`, `POST /api/v1/learning/tasks/{id}:feedback`, `GET /api/v1/learning/questions`, `POST /api/v1/learning/questions/{id}:answer`, `GET /api/v1/learning/files/students`, `GET /api/v1/learning/files/eligible-lessons`。
- **UI 问题**：没有独立 CSS 文件（复用 `learning-plans.component.css`）；课件资料、课后作业、课后答疑三块截然不同的业务硬平铺在同一长页面中。
- **ADHD 体验风险**：资料与作业混排，学生找作业时被课件打断，认知切换成本高。
- **性能风险**：中等。
- **重构优先级**：**P1**（教学闭环高频操作页，需拆分为清晰的 Tab 分区）。

### 19. 错题本与分类复习页 (MistakeNotesPage)
- **URL**：`/learning/mistakes`
- **组件文件路径**：`frontend/src/app/features/learning/mistake-notes.component.ts` (配合 `mistake-notes.component.html`)
- **引用的共享组件**：无
- **业务目标**：学生沉淀错题与原因、规划复习日程、导师一键转化复习任务、确认真正掌握。
- **主要用户角色**：学生、导师、家长（家长只读）。
- **核心交互**：录入错题（题干、原因、标签、复习日）、多维筛选分类、修改状态、导师转换为课后复习任务。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/mistakes`, `POST /api/v1/learning/mistakes`, `PUT /api/v1/learning/mistakes/{id}`, `DELETE /api/v1/learning/mistakes/{id}`, `POST /api/v1/learning/mistakes/{id}:task`, `GET /api/v1/learning/files/students`, `GET /api/v1/learning/files/eligible-lessons`。
- **UI 问题**：无独立 CSS 文件；表单占位过大；错题卡片全是原生文字，不支持公式或图片展示。
- **ADHD 体验风险**：文字录入要求高，缺乏「极简速记」模式；筛选结果缺乏卡片式记忆刷题感（Flashcard 体验）。
- **性能风险**：低。
- **重构优先级**：**P1**（学习过程管理特色功能，需提升卡片与刷题复习体验）。

### 20. 阶段小测与学习报告页 (AssessmentsPage)
- **URL**：`/learning/assessments`
- **组件文件路径**：`frontend/src/app/features/learning/assessments.component.ts`
- **引用的共享组件**：`<nur-queue-pager>`
- **业务目标**：导师录入客观单选题、发布小测、学生作答与单次即时评分、导师批注评语、呈现全量真实学习证据报告。
- **主要用户角色**：学生、导师、家长（家长只读）。
- **核心交互**：导师出题（最多 20 题）与草稿保存/发布/归档、学生单选题提交并计分、导师撰写评语、查看综合学习证据大盘（成绩趋势、课堂笔记）。
- **依赖的 API 服务/端点**：`GET /api/v1/learning/assessments`, `POST /api/v1/learning/assessments`, `PUT /api/v1/learning/assessments/{id}`, `POST /api/v1/learning/assessments/{id}/publish`, `POST /api/v1/learning/assessments/{id}/archive`, `POST /api/v1/learning/assessments/{id}/submit`, `POST /api/v1/learning/assessments/{id}/comment`, `GET /api/v1/learning/assessments/report`, `GET /api/v1/learning/plans/students`, `GET /api/v1/parent/me/children`, `GET /api/v1/learning/files/eligible-lessons`。
- **UI 问题**：**严重内联 template 与内联 styles**；排版粗糙原始，表单标签密集，答题卡、小测列表与报告大盘堆积在同一长页面中。
- **ADHD 体验风险**：答题与出题界面缺乏沉浸感与清晰专注模式；题目间距紧凑容易引起视觉疲劳。
- **性能风险**：低，但多重数据聚合拉取。
- **重构优先级**：**P1**（教学闭环关键验收页，需提供沉浸式出题与答题卡片）。

---

## 四、共享组件清单与状态完备性矩阵（Shared Components）

全站目前共有 **10 个共享组件**（位于 `frontend/src/app/shared/components/` 及 `layout/`）：

| 共享组件 Selector | 文件路径 | 被谁引用 (Consumers) | Hover 态 | Focus-Visible 态 | Disabled 态 | Loading 态 | Error 态 | Empty 态 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `nur-calendar-export` | `shared/components/calendar-export.component.ts` | `ParentDashboard`, `StudentDashboard`, `RecurringAvailability` | ❌ (内联原生) | ⚠️ (靠全局) | ✅ `[disabled]` | 🟡 仅文字切换 | ❌ | ❌ |
| `nur-garden-scene` | `shared/components/garden-scene/garden-scene.component.ts` | `AuthPortal`, `Landing` | ➖ (纯装饰) | ➖ (纯装饰) | ➖ (纯装饰) | ➖ | ➖ | ➖ |
| `nur-learning-attachments` | `shared/components/learning-attachments.component.ts` | `LearningMaterials`, `LearningPlans`, `StudentQuestions` | ⚠️ 基础按钮 | ⚠️ (靠全局) | ✅ `[disabled]` | 🟡 `busy()` | ✅ notice() | ✅ 「暂无附件」 |
| `nur-notice-center` | `shared/components/notice-center.component.ts` | 4 大工作台 + `StudentArchive`, `StudentGrowth`, `StudentQuestions` | ✅ 悬停高亮 | ✅ 焦点环 | ✅ 禁用翻页 | ✅ `loading()` | ✅ 失败重试 | ✅ 「暂无未读」 |
| `nur-queue-pager` | `shared/components/queue-pager.component.ts` | `AdminDashboard`, `Assessments`, `SupportCases`, `NoticeCenter` | ✅ 悬停样式 | ✅ 焦点环 | ✅ 边界禁用 | ✅ `busy()` 禁用 | ❌ | ✅ 0 页提示 |
| `nur-storage-quota` | `shared/components/storage-quota.component.ts` | `LearningMaterials`, `Messages`, `SupportCases` | ⚠️ 基础按钮 | ⚠️ (靠全局) | ✅ `[disabled]` | ✅ 「读取容量…」 | ✅ `role="alert"` | ✅ 0 字节显示 |
| `nur-teacher-card` | `shared/components/teacher-card/teacher-card.component.ts` | `Landing`, `Teachers` | ✅ 阴影与微位移 | ✅ 焦点环明确 | ✅ 按钮禁用 | ❌ | ❌ | ❌ |
| `nur-teacher-compare` | `shared/components/teacher-compare.component.ts` | `Match`, `Teachers` | ✅ 移除按钮 | ✅ 区域可聚焦 | ✅ (无禁用态) | ❌ | ❌ | ✅ `@if` 隐退 |
| `nur-teacher-profile-editor` | `shared/components/teacher-profile-editor.component.ts` | `TeacherDashboard` | ⚠️ 基础按钮 | ⚠️ (靠全局) | ✅ `[disabled]` | 🟡 仅文字切换 | ✅ 验证提示 | ✅ 未填写回退 |
| `nur-site-header` / `footer` | `layout/site-header/`, `layout/site-footer/` | `AppComponent` (全站外壳) | ✅ 品牌色悬停 | ✅ 焦点环规范 | ➖ | ➖ | ➖ | ➖ |

### 诊断小结：
共享组件中，`notice-center`、`queue-pager`、`teacher-card` 的交互与状态较为完备；而 `calendar-export`、`storage-quota`、`teacher-profile-editor` 采用纯内联 template 且缺乏统一的微交互样式，直接裸写了 style 属性，未能充分复用 Design Tokens。

---

## 五、样式系统与品牌 CSS 锁机制（Design System & Brand CSS Lock）

### 5.1 样式架构全景
前端全局样式入口位于 `frontend/src/styles/styles.css`，由四部分级联导入：
1. `tokens.css`：全站 Design Tokens 唯一定义源（颜色、圆角、阴影、间距、字体栈、动效曲线）。
2. `base.css`：重置与基础规范（HIG 44pt 最小点击热区、无刺眼红框、双通道校验态、`prefers-reduced-motion` 抑制规则、`:focus-visible` 焦点环）。
3. `components.css`：全站通用 UI 类库（`.nur-container`, `.nur-section`, `.nur-btn`, `.nur-card`, `.nur-kicker`, 标题层级）。
4. `brand-presentation.css`：由阶段 8 从组件中提取出的深色竹林品牌装饰样式（限定于 `nur-auth-portal` 作用域）。

### 5.2 双主题视觉架构事实
平台实际并存**两套视觉体系**：
- **营销与鉴权深色体系 (Dark Bamboo Theme)**：用于 Landing 营销页与 Auth 登录门户。以深墨绿（`--bamboo-base: #173028`, `--bamboo-mid: #1f3f35`）、檀木暖金（`--sandalwood: #dda36f`）、玉白（`--jade-white: #f0f6f3`）为主，辅以月洞门、雨丝与楠竹场景层，营造杜甫《春夜喜雨》的东方书卷沉浸感。
- **工作台与功能业务浅色体系 (Paper White Theme)**：用于学生/家长/教师/运营工作台及全部学习服务页。以宣纸米白（`--bg-page: #F8F7F2`）、纯白卡片（`--bg-card: #FFFFFF`）、墨青主文（`--text-main: #30443F`）、灰青辅助（`--text-muted: #5E6E68`）为主，突出清雅、克制、低认知负担的日常使用体验。

### 5.3 `tools/verify_brand_css.cjs` 锁定机制与代价分析
- **脚本目标与机制**：
  - 该脚本以 Git 基线 `096e474`（阶段 7 收口检查点）为参照。
  - 使用 `postcss` 解析基线中的三个核心组件样式：
    1. `frontend/src/app/features/auth/auth-portal.component.css`
    2. `frontend/src/app/features/landing/landing.component.css`
    3. `frontend/src/app/features/match/match.component.css`
  - 提取其中的每一个 CSS declaration（`prop:value` + 是否 `!important`），排序后与当前工作区的上述三文件加上 `frontend/src/styles/brand-presentation.css` 进行集合比对。
  - 核心断言：`JSON.stringify(before) !== JSON.stringify(after)` 则直接抛出 `Error: Brand CSS declarations changed`！
- **实测现状**：当前执行 `node tools/verify_brand_css.cjs` 严格通过，精确锁定了 **1,065 条原始 CSS 声明**。
- **改营销页 CSS 时代价与重构红线**：
  - **严禁擅自修改、删除或重命名** 这三个组件及其在 `brand-presentation.css` 中的既有 CSS 属性与数值。
  - 任何针对 Landing、Auth、Match 的样式改动，若删除了原有声明（即便合并或简写），都会导致持续集成验证失败！
  - **安全重构策略**：重构后续代理若需为营销页增加新特性，必须以**纯增量（Additive Only）** 方式定义新类名或新文件，绝对不可破坏既有 1,065 条声明的存在性。

---

## 六、认证与登录全流程深度分析（Authentication & Routing Hand-off）

### 6.1 登录门户组件 (`AuthPortalComponent`)
- 位于 `frontend/src/app/features/auth/auth-portal.component.ts`。
- 提供四角色切换 Tab（学生、家长、导师、运营），支持通过 URL query param `?role=...` 预设当前角色。
- 运营和家长仅限密码通道；学生和导师可选短信验证码或密码通道；同时支持学生/家长/导师在线自助注册。

### 6.2 登录请求体契约
- 密码登录：`POST /api/v1/identity/auth/login:password`，请求体：
  `{ role: Role, account: string, password: string }`
- 短信登录：`POST /api/v1/identity/auth/login:sms`，请求体：
  `{ role: Role, phone: string, smsCode: string }`
- 注册请求体：
  - 学生：`{ displayName, phone, password }`
  - 家长：`{ displayName, phone, password }`
  - 导师：`{ displayName, phone, password, school, highestDegree, yearsOfTeaching }`

### 6.3 令牌与 Cookie 处理机制（D5 架构成果）
- **安全红线**：前端 `localStorage` **严禁存储 accessToken 或 refreshToken**。
- **持久化内容**：`localStorage` 仅存储非敏感会话摘要（`StoredSession`：`accountNo`, `displayName`, `role`, `expiresAtEpochMs`）。若摘要过期，在应用初始化时自动清除。
- **HttpOnly Cookie**：认证成功后，后端通过响应头 `Set-Cookie` 下发 HttpOnly 安全 Cookie；前端通过 `auth.interceptor.ts` 设置 `withCredentials: true`，在所有请求中由浏览器原生自动附带 Cookie。
- **内存信号**：登录响应中的 `AuthResponse` 临时驻留在 `AuthService` 的 `sessionSignal` 内存信号中。若内存中存在 accessToken，请求拦截器会补充附加 `Authorization: Bearer <token>` 请求头。
- **写操作强幂等保护**：拦截器对所有 `POST`, `PUT`, `PATCH`, `DELETE` 请求统一自动注入 `Idempotency-Key: crypto.randomUUID()`（严格 UUIDv4）。

### 6.4 登录后分流逻辑
- 虽然用户在前端界面通过 Tab 选择了想要登录的角色，但在登录成功后，**最终的分流完全由后端响应中的 `res.role` 权威驱动**！
- `AuthService.navigateToRoleHome(res.role)` 依据后端返回的角色枚举映射至对应的控制台：
  - `STUDENT` -> `/student/dashboard`
  - `PARENT` -> `/parent/dashboard`
  - `TEACHER` -> `/teacher/dashboard`
  - `ADMIN` -> `/admin/dashboard`
- 此设计确保了用户无法通过前端假冒角色进入不属于自己的控制台。

---

## 七、API 调用层与状态管理架构（API Layer & State Management）

### 7.1 API Client 与 Service 清单
- **API 常量集中营**：`frontend/src/app/core/api/endpoints.ts` 集中定义了全站 REST 路径常量 `API.*`。
- **Client 生成现状**：**全站零代码生成**！没有使用 OpenAPI Generator 或 swagger-codegen，所有数据模型接口（interface）与 HTTP 请求全部由人工手写维护。
- **Service 清单现状**：
  1. `AuthService` (`frontend/src/app/core/auth/auth.service.ts`)：全站唯一的全局领域服务。
  2. `TeachersApi` (`frontend/src/app/core/api/teachers-api.ts`)：带抽象类的 DI 端口服务，提供 `HttpTeachersApiService` 与 `MockTeachersApiService` 两种实现。
  3. **其余 18 个业务组件现状**：**全部直接在组件类中 `inject(HttpClient)`**，手工组装 URL、手工传递参数、手工订阅 Observable！缺少领域 Service 层（Domain Services）。

### 7.2 错误处理与 Loading 状态管理
- **错误处理**：缺少全局 `HttpErrorInterceptor`。各组件在 subscribe 的 `error` 回调中各自提取 `e.error?.detail ?? '默认提示'`，写入组件局部的 `notice = signal('')`。
- **Loading 管理**：各组件分散声明 `loading = signal(false)` 或 `busy = signal(false)`，在操作前后手动翻转布尔值。部分复杂组件（如 `SupportCasesComponent`）通过自增版本号（`this.version = ++this.version`）来防护网络竞态与慢请求覆盖。

### 7.3 状态管理现状（Signals vs Store）
- 全站使用的是 **Angular 19 核心原生 Signals（`signal()`, `computed()`）**。
- **完全没有引入第三方 Store**（如 NgRx, Akita, NGXS）。
- **状态无持久跨页漫游**：除 `AuthService` 将登录态存入单例 Signal 并在 localStorage 保存摘要外，其余所有业务状态都局限在单组件生命周期内部。离开页面后状态彻底销毁，返回页面必须重新向后端发起全量 HTTP 请求，无前端缓存层。

---

## 八、响应式现状与 375px 基线表现（Responsive Architecture）

### 8.1 移动端断点现状
全站 CSS 中未在 `tokens.css` 中建立统一的响应式断点变量，各组件内的 `@media` 规则高度碎片化，代码中出现的断点多达 11 种：
- `1100px` (Auth 双栏折叠)
- `1024px` (Landing 间距缩紧)
- `960px` (Teachers 筛选栏折叠)
- `860px` (Header / Footer 导航收拢)
- `720px` (Garden 场景竹叶淡化)
- `700px` (Messages 聊天布局转单列)
- `640px` (Student / Admin / Auth 栅格与表单转单列)
- `600px` (Refunds 列表项转竖排)
- `560px` (Match 需求表单单列)
- `500px` (Assessments / Support 内边距压缩)
- `480px` (LearningPlans 进度网格重排)

### 8.2 375px 移动端基线证据
- **全局防护**：`base.css` 定义了 `body { overflow-x: clip; }` 与 `*, *::before, *::after { box-sizing: border-box; }`，有效避免视口产生横向滚动条。
- **表格适配**：横向对比组件 `teacher-compare.component.ts` 在移动端采用 `<div class="scroll" tabindex="0" role="region">` 结合 `overflow-x: auto`，保障小屏可滑动。
- **历史缺陷修复证据**：在阶段 7 验收中曾排查出「咨询附件在 375px 下原生输入框溢出 7px」的缺陷，后续已通过 `box-sizing: border-box` 与百分比宽度收口修复。
- **现存 375px 体验痛点**：
  1. `TeachersComponent` 筛选栏在 375px 下垂直高度过长，用户需滑动整屏才能看到首条教师卡片；
  2. `TeacherDashboardComponent` 的今日课表在 375px 下，「改期」与「课堂准备」两个按钮紧挨挤在一起，点击热区容易重叠。

---

## 九、动效系统与无障碍抑制现状（Animations & Accessibility）

### 9.1 动画定义与位置
- 动画主要定义在 `styles/base.css` (`@keyframes nur-enter`)、`styles/brand-presentation.css` (`mistDrift`, `mistBreathe`, `glowBreathe`)、`shared/components/garden-scene/` (`bambooBreeze`, `rainDrizzle`, `rippleGrow`) 以及 `landing.component.css` 和 `auth-portal.component.css` (`@keyframes heroRise`)。

### 9.2 「内容依赖动画触发才可见」与 hero-rise 假消失陷阱
- **陷阱机制剖析**：
  在 `auth-portal.component.css` 与 `landing.component.css` 中，元素被赋予了入场类：
  ```css
  .hero-rise {
    opacity: 0;
    animation: heroRise 0.9s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  }
  ```
  如果开发者粗暴地在全局执行以下媒体查询：
  ```css
  @media (prefers-reduced-motion: reduce) {
    * { animation: none !important; }
  }
  ```
  则 `heroRise` 动画根本不会执行，元素将永远停留在其初始声明的 `opacity: 0`！导致整屏关键标题与卡片在开启「减少动效」时**彻底假消失（空白）**！
- **现行正确方案与代码证据**：
  在 `styles/base.css` 中，项目采用了 Apple HIG 推荐的优雅降级方案：
  ```css
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
      scroll-behavior: auto !important;
    }
  }
  ```
  将动画时长压缩至 0.01 毫秒，使动画瞬间跑完并应用 `forwards` 的最终帧（`opacity: 1`），既尊重了用户减弱动效的生理需求，又确保了内容 100% 正常可见。同时在组件样式中显式重申 `.hero-rise { animation: none; opacity: 1; }` 作为双重保障。

---

## 十、性能现状与大组件分析（Performance & Budget Analysis）

### 10.1 `angular.json` 构建预算配置
- **初始 Chunk 预算**：`maximumWarning: 1MB`, `maximumError: 2MB`。
- **单组件样式预算**：`maximumWarning: 8kB`, `maximumError: 16kB`。
  *(注：该 8kB 预算曾是阶段 8 的重点治理对象，任何单组件 CSS 超过 8kB 构建就会警告，超过 16kB 构建直接失败；目前全部组件均控制在 8kB 以内，生产构建零 Warning。)*

### 10.2 静态资源与 Heavy Assets 审查
- **大图与静态资源**：`frontend/public/` 下仅有 `favicon.svg` 与 `_redirects`，**全工程没有引入任何重型外部位图 (PNG/JPG)**，所有视觉图形（竹林、月洞门、冰裂纹、印章）全部使用轻量矢量 SVG 与纯 CSS 渐变渲染。
- **Canvas / 3D 审查**：全站无 WebGL、Three.js 或原生 `<canvas>` 依赖，不存在 GPU 显存泄漏风险。

### 10.3 超级大组件排行（Top 20 源码文件行数统计）

按源码行数严格降序排列的前 20 个 `.ts` / `.html` 文件如下：

| 排名 | 代码行数 | 文件路径 | 组件/职责说明 | 风险等级 |
|:---:|:---:|---|---|:---:|
| 1 | **612** | `features/teacher/teacher-dashboard.component.ts` | 导师工作台主逻辑 (8 大业务混排) | 🔴 极高 (重构首选) |
| 2 | **527** | `features/match/match.component.ts` | 找老师与精准匹配主逻辑 | 🔴 极高 (重构首选) |
| 3 | **392** | `features/teacher/teacher-dashboard.component.html` | 导师工作台模板 (多重弹窗与日历) | 🔴 高 |
| 4 | **390** | `features/student/student-dashboard.component.ts` | 学生工作台主逻辑 | 🟡 中偏高 |
| 5 | **326** | `features/match/match.component.html` | 找老师需求与推荐模板 | 🟡 中偏高 |
| 6 | **290** | `features/parent/parent-dashboard.component.ts` | 家长工作台主逻辑 (代付与支付状态机) | 🟡 中 |
| 7 | **264** | `features/auth/auth-portal.component.html` | 统一鉴权门户模板 | 🟢 稳定受保 |
| 8 | **249** | `features/student/student-dashboard.component.html` | 学生工作台模板 | 🟡 中 |
| 9 | **248** | `features/admin/admin-dashboard.component.ts` | 运营治理控制台主逻辑 (五大队列) | 🟡 中 |
| 10 | **246** | `features/parent/parent-dashboard.component.html` | 家长工作台模板 | 🟡 中 |
| 11 | **236** | `features/auth/auth-portal.component.ts` | 统一鉴权门户主逻辑 | 🟢 稳定受保 |
| 12 | **234** | `shared/components/garden-scene/garden-scene.component.html` | 竹林场景 SVG 模板 | 🟢 纯展示 |
| 13 | **222** | `features/teachers/teachers.mock.ts` | 导师静态展示数据 | 🟢 纯数据 |
| 14 | **218** | `features/admin/admin-dashboard.component.html` | 运营控制台模板 (五队列纵向堆叠) | 🟡 中 |
| 15 | **209** | `features/teachers/teachers.component.html` | 导师公开检索模板 | 🟡 中 |
| 16 | **209** | `features/landing/landing.component.html` | 营销首页模板 | 🟢 稳定受保 |
| 17 | **201** | `shared/components/notice-center.component.ts` | 站内通知中心组件 | 🟢 共享组件 |
| 18 | **176** | `features/teachers/teachers.component.ts` | 导师公开检索主逻辑 | 🟢 正常 |
| 19 | **170** | `features/landing/landing-data.ts` | 营销首页静态数据源 | 🟢 纯数据 |
| 20 | **134** | `features/learning/learning-plans.component.ts` | 学习计划主逻辑 | 🟢 正常 |

---

## 十一、「裸文字/缺信息结构」页面专项诊断（Raw Text Pages Diagnosis）

经全站逐页走查，以下 **7 个页面** 呈现出明显的「裸 HTML 标签堆砌、缺乏现代信息架构、内联原生表单、UI 停留在原型阶段」的严重短板：

1. **`TeacherDetailComponent` (`/teachers/:teacherId`)**：
   - 表现：内联 template，无导师头像；在售课程与可约时间全用无样式的原生 `<ul><li>`；评价汇总仅有一行单薄文字；**缺少学员评价列表区**。
2. **`AssessmentsComponent` (`/learning/assessments`)**：
   - 表现：内联 template + 内联 styles；出题区全为原生 `<fieldset>`, `<legend>`, `<select>`，题目间距狭窄；小测列表与学习报告大盘硬堆在一起，视觉缺乏卡片化包装。
3. **`SupportCasesComponent` (`/support`)**：
   - 表现：内联 template + 内联 styles；课程关联、工单列表、工单详情、证据配额、多轮沟通记录全部纵向平铺，边框使用硬编码颜色（`#cad9cf`, `#dce6df`），脱离了 Tokens 规范。
4. **`StudentArchiveComponent` (`/student/archive`)**：
   - 表现：内联 template，大量使用行内样式（`style="display:flex;gap:24px..."`）；统计数据为裸 `<div><p>`，课堂记录只有一条简陋的左边框，缺乏时间线维度。
5. **`StudentQuestionsComponent` (`/student/questions`)**：
   - 表现：内联 template，大量行内样式；提问表单与历史记录同屏，没有折叠或抽屉隔离；缺少操作反馈。
6. **`StudentGrowthComponent` (`/student/growth`)**：
   - 表现：内联 template，统计数据使用内联样式；没有采用可视化环形图或进度卡片，纯粹靠大号粗体字呈现数字。
7. **`RefundsComponent` (`/refunds`)**：
   - 表现：结构极其简陋，订单列表与申请表单内联在同一区块，没有标准的申请步骤指引，也没有退款状态时间轴（Timeline）。

---

## 十二、后端 Controller 与前端调用全景对照（Backend API Cross-Verification）

结合项目既有《交付报告_无UI功能清单_2026-10-11.md》，本次侦查对后端 **29 个 Controller 类、约 150 个 REST 端点** 与前端实际调用进行了严格交叉比对，核实了 **15 项无 UI 缺口**、**6 项重复入口** 与 **7 项前端死常量**。

### 12.1 15 项「有后端无前端入口」功能全量清单

| 分类 | 编号 | 业务功能 | 后端 REST 端点 | 当前前端现状 | 建议补充的落点页面与组件 |
|---|:---:|---|---|---|---|
| **A. 交易履约闭环 (P0)** | **A1** | 取消预约 (两阶段取消) | `POST /api/v1/booking/bookings/{id}:cancel/preview`<br>`POST /api/v1/booking/bookings/{id}:cancel/execute` | `endpoints.ts` 已定义但全站 0 调用，无按钮 | 学生/家长工作台课程卡 + 导师工作台今日课程卡加「取消预约」二次确认弹窗 |
| | **A2** | 开始上课 / 完成课程 | `POST /api/v1/booking/bookings/{id}:start`<br>`POST /api/v1/booking/bookings/{id}:complete` | 后端全库仅该端点可驱动状态流转，前端 0 调用，导致 CONFIRMED 无法流转至 COMPLETED | 导师工作台今日课程卡加「开始上课」与「完成课程」（并入下课考勤流程） |
| | **A3** | 我的预约列表与详情 | `GET /api/v1/booking/me/bookings`<br>`GET /api/v1/booking/bookings/{id}` | 前端 0 调用，学生只能看下一节课，无预约总览 | 学生工作台新增「我的预约」卡片（含分页与状态筛选） |
| **B. 学习成长服务 (P1)** | **B1** | 学习画像与目标编辑 | `GET/PUT /api/v1/learning/profile` | 前端 0 调用，学生无法编辑长期目标与学段 | 学生工作台或成长中心 `/student/growth` 增加「学习目标」编辑卡 |
| | **B2** | 学生个人资料编辑 | `GET/PUT /api/v1/student/me/profile` | 前端 0 调用，学生无资料维护入口 | 学生工作台设置区增加「个人资料」卡片 |
| | **B3** | 关闭答疑 | `POST /api/v1/learning/questions/{id}:close` | 前端 0 调用，答疑永远停在 ANSWERED 无法关闭 | 课后答疑页 `/student/questions` 卡片加「关闭答疑」按钮 |
| | **B4** | 教师课堂记录录入 | `POST+GET /api/v1/learning/teacher/lesson-records` | 前端 0 调用，学生档案有展示但教师无录入界面 | 并在 A2 下课流程中，完成课程时提示录入课堂记录 |
| | **B5** | 我的学生与档案查看 | `GET /api/v1/learning/teacher/students`<br>`GET /api/v1/learning/teacher/students/{id}` | 前端 0 调用，教师看不到名下学生总览 | 导师工作台增加「我的学生」卡片，点击查看学生档案 |
| | **B6** | 课前准备任务生成 | `POST+GET /api/v1/classroom/bookings/{id}/preparation-tasks` | 学生端有勾选，但教师端无生成按钮，学生永远看空 | 导师工作台「课堂准备」弹窗加「生成课前任务清单」 |
| | **B7** | 布置的课后任务列表 | `GET /api/v1/learning/teacher/tasks` | 前端 0 调用，教师无历史任务总览 | 导师教学资料页 `/learning/materials` 增加任务总览 |
| **C. 评价体系 (P1)** | **C1** | 教师公开评价列表 | `GET /api/v1/review/reviewees/{id}/reviews` | 导师详情页仅有一行汇总文字，无具体评价列表 | 导师公开详情页 `/teachers/:id` 下方补齐「学员评价」分页列表 |
| | **C2** | 我收到的评价 (教师端) | `GET /api/v1/review/me/reviews-received` | 教师看不到学员对自己的评价与打分 | 导师工作台增加「学员评价反馈」卡片 |
| **D. 运营治理 (P2)** | **D1** | 平台全局参数配置 | `PUT /api/v1/admin/configs/{key}` | 运营后台无参数界面，改抽成比例需调 API | 运营控制台增加「平台配置」卡片（参数查阅与修改） |
| | **D2** | 登录限流误锁解封 | `POST /api/v1/admin/security/login-throttle:reset` | 误锁 15 分钟后客服无法在后台解锁 | 运营控制台「账户队列」增加「解除登录限流」按钮 |
| **E. AI 集成 (可选)** | **E1** | Agent API Token 管理 | `POST/DELETE /api/v1/identity/api-tokens` | 仅支持后端调用，前端无管理界面 | 可按需在运营后台增加「Agent 凭证」管理卡 |

### 12.2 6 项重复/旧入口（明确不必补 UI，标注下线候选）
1. `POST /booking/booking-requests/{id}:decide`：与 `/teacher/me/booking-requests/{id}:decide` 功能重复，前端已接后者。
2. `GET /learning/tasks`：独立任务列表，已被 `/learning/growth` 内嵌替代。
3. `GET /learning/teacher/questions`：教师答疑队列，已被 `/learning/files/questions` 替代。
4. `GET /learning/assessments/{id}`：小测详情接口，已被小测列表接口内联替代。
5. `GET /matching/teachers:recommend`：旧私有推荐接口（固定20条），已被公开检索分页取代。
6. `POST /student/me/preparation-tasks`：旧版生成端点，已被 `/classroom/...` 契约替代。

### 12.3 7 项前端死常量清单（位于 `endpoints.ts`，建议清理）
- `API.parent.childOverview` ⚠（指向不存在的后端路由）
- `API.parent.walletTransactions` ⚠（指向不存在的后端路由）
- `API.parent.lessonProgress` ⚠（指向不存在的后端路由）
- `API.parent.reviews` ⚠（指向不存在的后端路由）
- `API.student.preparationTasks` ⚠（指向不存在的后端路由，真实路由无 lessons 前缀）
- `API.student.completeTask`（路由存在但实际使用 classroom 版）
- `API.student.profile`（路由存在但前端无 UI 入口，可保留给 B2 使用）

---

## 十三、问题清单综合排序（Prioritized Issue Backlog）

综合业务断点、代码臃肿度、视觉体验与 ADHD 无障碍标准，全站重构问题清单排序如下：

### 🔴 P0 级（最高优先级 · 交易闭环卡点与超级臃肿组件）
1. **[交易断环] A2 开始上课与完成课程缺失**：导师无法在网页上将预约流转至 `COMPLETED`，导致整个评价与记录链条无法解锁。
2. **[交易断环] A1 两阶段取消预约缺失**：学生、家长、导师工作台全站没有任何取消预约按钮，用户只能改期不能取消。
3. **[交易断环] A3 学生我的预约列表缺失**：学生只能看下一节课，缺乏完整的历史与待履约预约总览卡。
4. **[巨型组件] 导师工作台拆分 (`teacher-dashboard.component.ts`, 612 行)**：信息严重过载，需拆分为独立卡片组件并补齐 A1, A2, B4, B5, C2, B6。
5. **[巨型组件] 匹配页拆分 (`match.component.ts`, 527 行)**：需求收集与推荐/档期选择揉成一团，需改造成向导式步进组件。

### 🟡 P1 级（高频控制台重构与信息结构化补齐）
6. **[公开详情页重构] `teacher-detail.component.ts`**：消除裸文字，设计现代化导师主页，补齐 **C1 教师公开评价列表**。
7. **[学生工作台体验] `student-dashboard.component.ts`**：清理顶部裸 `<nav>` 散落链接，补齐 **B1 学习画像** 与 **B2 个人资料**。
8. **[评价闭环] 补齐 C2 导师收到的评价列表**：并在导师工作台提供反馈展示。
9. **[教学过程闭环] 补齐 B4 课堂记录录入与 B6 课前任务生成**：融合到导师课堂准备与结课弹窗。
10. **[学生管理闭环] 补齐 B5 教师端我的学生与档案查看**。
11. **[答疑体验] 补齐 B3 关闭答疑**：并在 `/student/questions` 引入状态过滤与草稿暂存。
12. **[四大原生页面重构]**：重构 `assessments.component.ts`、`support-cases.component.ts`、`student-archive.component.ts`、`refunds.component.ts`，消除内联 template 与行内 style，建立设计系统卡片。
13. **[运营后台重构] `admin-dashboard.component.ts`**：将五大队列重构为 Tab 切换结构，并补齐 **D1 平台配置** 与 **D2 限流解锁**。

### 🟢 P2 级（高级工具与展示优化）
14. **[排课工具可视化] `recurring-availability.component.ts`**：将重复档期列表升级为直观的周日历甘特图（Week View）。
15. **[统一响应式断点]**：在 `tokens.css` 中规范化断点变量，收敛 11 种混乱的 `@media` 查询。
16. **[清理死常量]**：清理 `endpoints.ts` 中 7 项无用及指向错误路由的常量。
17. **[可选管理界面] 补齐 E1 Agent API Token 管理**。

---

## 十四、给后续重构代理的「文件所有权划分建议」（File Ownership Recommendations）

为避免后续多个 AI Agent 或开发人员并行重构时发生代码冲突，建议按以下**所有权矩阵**严格划分文件边界：

```
                    ┌──────────────────────────────────────────────┐
                    │               全局样式与规范守门               │
                    │         Agent 02 (Design System & Tokens)    │
                    │  - frontend/src/styles/tokens.css            │
                    │  - frontend/src/styles/components.css        │
                    │  - frontend/src/styles/base.css              │
                    │  - tools/verify_brand_css.cjs (只读守护)      │
                    └──────────────────────┬───────────────────────┘
                                           │
         ┌─────────────────────────────────┼─────────────────────────────────┐
         │                                 │                                 │
         ▼                                 ▼                                 ▼
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│     Agent 03 (交易闭环)   │   │    Agent 04 (导师工作台)  │   │    Agent 05 (学生与家长) │
│ - features/teachers/    │   │ - features/teacher/     │   │ - features/student/     │
│   (teachers, detail)    │   │   (dashboard 拆分)      │   │   (dashboard, growth,   │
│ - features/match/       │   │ - features/scheduling/  │   │    archive, questions)  │
│   (match.component 拆分)│   │   (series)              │   │ - features/parent/      │
│ - features/refunds/     │   │ - 新增: teacher-students│   │   (dashboard)           │
│ - 承接: A1, A3, C1      │   │ - 承接: A2, B4, B5, C2  │   │ - 承接: B1, B2, B3      │
└─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘
                                           │
         ┌─────────────────────────────────┴─────────────────────────────────┐
         │                                                                   │
         ▼                                                                   ▼
┌─────────────────────────┐                                 ┌─────────────────────────┐
│    Agent 06 (学习与客服)  │                                 │     Agent 07 (运营治理)  │
│ - features/learning/    │                                 │ - features/admin/       │
│   (plans, materials,    │                                 │   (dashboard 拆分)      │
│    mistakes, assessment)│                                 │ - core/api/endpoints.ts │
│ - features/support/     │                                 │ - 承接: D1, D2, E1      │
│ - features/messages/    │                                 │                         │
└─────────────────────────┘                                 └─────────────────────────┘
```

### 14.1 详细责任田分工细则：
1. **Agent 02（设计系统与共享组件代理）**：
   - **独占写入**：`frontend/src/styles/tokens.css`、`components.css`、`base.css`，以及 `shared/components/` 下的通用组件（`calendar-export`, `queue-pager`, `storage-quota`）。
   - **红线**：严禁修改 `brand-presentation.css` 以及 `auth/landing/match` 既有样式，确保 `node tools/verify_brand_css.cjs` 随时通过。
2. **Agent 03（公共师资检索与交易发起代理）**：
   - **独占写入**：`features/teachers/` 全量文件、`features/match/` 全量文件、`features/refunds/` 全量文件。
   - **重点攻坚**：重构详情页补齐评价列表（C1），拆分 Match 超级组件，落实两阶段取消（A1）。
3. **Agent 04（导师端教学履约与工作台代理）**：
   - **独占写入**：`features/teacher/` 全量文件、`features/scheduling/`。
   - **重点攻坚**：拆解 612 行的巨型 `teacher-dashboard`；落实开始/完成课程（A2）、课堂记录录入（B4）、我的学生（B5）、收到评价（C2）、课前任务生成（B6）。
4. **Agent 05（学生与家长端工作台代理）**：
   - **独占写入**：`features/student/` 全量文件、`features/parent/` 全量文件。
   - **重点攻坚**：重构两个 Dashboard；清理散落链接；落地我的预约列表（A3）、学习目标（B1）、个人资料（B2）、关闭答疑（B3）。
5. **Agent 06（学习过程管理与互动协作代理）**：
   - **独占写入**：`features/learning/`、`features/support/`、`features/messages/`。
   - **重点攻坚**：重构 `assessments` 与 `support-cases` 两个原生内联页面，引入优雅卡片与多区 Tab。
6. **Agent 07（运营后台治理与契约清理代理）**：
   - **独占写入**：`features/admin/`、`core/api/endpoints.ts`。
   - **重点攻坚**：为运营控制台引入 Tab 布局；补齐平台参数（D1）与限流解锁（D2）；清理死常量。

---

## 十五、结论与交接总结

本摸底报告以 100% 真实源码为依据，全面梳理了全站 20 个页面、10 个共享组件与 29 个后端 Controller 的架构现状。全站不存在重型图片与 WebGL 负担，主要矛盾集中在：
1. **交易履约存在 4 项致命 P0 缺口（取消预约、完成课程、我的预约列表）**；
2. **三大工作台（尤其是导师工作台 612 行）严重臃肿过载**；
3. **后期追加的 7 个学习/客服页面严重缺乏信息结构与设计系统整合（裸 HTML 堆叠）**；
4. **品牌 CSS 锁（1,065 条声明）形成了严格的保护屏障，改动必须保持纯增量或等价**。

后续重构代理可直接依据本文档的逐页档案、优先级清单及文件所有权建议分兵突击。
