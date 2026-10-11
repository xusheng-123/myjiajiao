# 视觉 Workshop 使用说明（2026-10-11）

## 看什么
打开方式（任选其一）：
- 本会话期间浏览器已在跑：http://127.0.0.1:8090/workshop/
- 以后重开：`cd docs/ui-redesign-2026-10-11 && python -m http.server 8090 --bind 127.0.0.1`，再访问上面地址

Workshop 单页包含五个区：
1. **方向候选**：A 月洞门借景 / B 素笺留白 / C 雨雾浸润·海棠漏窗 / D 月洞门黛绿暖金（带活页面）
2. **设计令牌**：加深版苔青色板（含全部 HEX）、楷体标题字体演示、状态色
3. **组件实验室**：活的按钮（点击有苔青涟漪）/卡片/徽章/表单错误态/Tabs/Toast/空状态/骨架屏/表格
4. **动效实验室**：润物动效规范与可点演示，遵循 prefers-reduced-motion
5. **页面方案**：首页桌面/移动、登录、学生/家长/导师/运营工作台、组件矩阵共 8 张高保真方案图

## 四个方向速评
| 方向 | 家族 | 气质 | 注意点 |
|---|---|---|---|
| A 月洞门借景 | 苔青+楷体 | 情感共鸣最强 | 摄影噪点需控制 |
| B 素笺留白 | 苔青+楷体 | 最克制、ADHD 最友好、工程成本最低 | 个性稍弱 |
| C 雨雾浸润 | 苔青+楷体 | 居中构图，移动端最友好 | 海棠漏窗占框景预算 |
| D 黛绿暖金 | 黛绿+宋体 | 唯一真实摄影 Hero，规格最完整（性能/无障碍量化） | 与 A/B/C 不同色族字体族；自检见 `方向D自检清单.md` |

方向 D 活页面：http://127.0.0.1:8090/workshop/hero-d.html （桌面）；375px 移动效果见 `hero-d-mobile.html` 或截图 `images/workshop-d-mobile-*.png`。

## 文件清单
- `workshop/index.html` + `assets/tokens.css` / `workshop.css` / `workshop.js` —— 苔青家族工作坊主站
- `workshop/hero-d.html` + `assets/tokens-d.css` + `assets/hero/*` —— 方向 D 独立活页面与图资产
- `images/*.png` 原图（每张 1.5~2.4MB）· `images/preview/*.jpg` 1200px 预览（共 2.15MB）
- `audit/frontend-audit.md` 前端全量侦查 · `audit/visual-direction.md` 视觉决策 · `audit/design-constraints.md` 用户硬约束
- `workshop/方向D自检清单.md`

## 下一步（待用户拍板）
选定方向家族（苔青楷体 vs 黛绿宋体）→ 才能进入全站实施。营销页 CSS 有品牌锁（tools/verify_brand_css.cjs，基线 096e474 逐条比对），实施时需同步更新锁基线，不能绕过。
