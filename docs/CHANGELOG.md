# CHANGELOG — 变更与验证记录（新条目置顶）

> 详细叙述见 `E:\线上家教平台\项目交接.md`（滚动事实源）。本表只做索引。

| 日期 | 轮次/条目 | 要点 | 验证 |
|---|---|---|---|
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
| — | **当前测试基线** | 单元 35 · 集成 28 · E2E 124 · 探针 0 · 配置门禁 38+ · RTO 55s | 见 PROJECT_HANDOFF §3 |
