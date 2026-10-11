# NURTURE 方向 D 设计系统 · 黛绿暖金（全站重构单一事实源）

> 2026-10-11 用户拍板：全站 UI/UX 重构采用**方向 D**。本文件是所有实现代理必须遵守的视觉/交互契约。
> 原型参照：`workshop/hero-d.html`（已验收的导航+Hero 活页面）；令牌以 `frontend/src/styles/tokens.css` 为最终落地。

---

## 1. 气质与禁区

苏州园林 · 雨后初晴：明亮、安静、湿润、有呼吸感。粉墙留白为体，黛绿为骨，暖金为韵。

**全站禁区（硬性）**：
- 禁深色底区块（旧版竹林暗色主题 `brand-presentation.css` / `--bg-deep` 体系废弃）
- 禁楷体/书法体、禁水墨晕染、禁竹剪影/竹林场景（`nur-garden-scene` 不再出现于新页面）
- 禁大模型 API 生成内容上屏、禁未经压缩的大图（摄影图全站 ≤2 张，须 AVIF+WebP 双格式）
- 禁纯装饰性持续动画；所有动效必须解释状态或引导注意

## 2. 色彩令牌（tokens.css 已落地，实现时一律用变量，禁写死色值）

| 令牌 | 值 | 用途 |
|---|---|---|
| `--bg-page` | `#F2F3EE` 雾白 | 页面主底 |
| `--bg-mist` | `#E6EBE5` 雾青 | 交替区块底（营销页区块严格交替 page/mist） |
| `--bg-card` | `#FFFFFF` | 卡片 |
| `--color-primary` | `#1E4D3E` 黛绿 | 主按钮/选中态/焦点环（白字对比度 9.6:1） |
| `--color-primary-hover` | `#173D31` | 主色悬停 |
| `--color-primary-soft` | `#E3ECE6` | 主色浅底（标签/选中底） |
| `--color-gold` | `#A8875A` 暖金 | **仅装饰与大字**（眉标英文、分隔饰、序号） |
| `--color-gold-text` | `#8E6B3E` | 暖金文字版（副标、强调短句） |
| `--text-title` | `#1E3A31` | 标题 |
| `--text-main` | `#2F3A36` | 正文 |
| `--text-muted` | `#5C6662` | 辅助 |
| `--line` | `rgba(30,58,49,0.14)` | 1px 黛瓦线（边框/分隔） |

状态色沿用令牌：`--color-success/-danger` 及其 `-bg`；校验态双通道（颜色+文字/图标），禁仅靠颜色。

旧令牌名全部保留为别名（`--color-accent`→gold、`--color-primary-bg`→primary-soft、`--border-subtle`→line 等），旧组件零改动自动换装。

## 3. 字体与排版

- 标题（H1/H2/卡片大标题/数字）：`--font-serif`（Source Han Serif SC / Noto Serif SC / Songti SC）。字重 600，letter-spacing 0.06–0.08em
- 正文/表单/表格/按钮：`--font-sans`。正文 1rem、行高 1.7–1.8
- 品牌英文 `NURTURE`：serif + letter-spacing 0.34–0.45em + `--color-gold`
- 眉标（eyebrow/kicker）：14px / 0.2–0.3em 字距 / muted 色，可配 48px×1px 细线
- 中文标题禁加粗到 700+；宋体 600 即有碑刻感

## 4. 布局与节奏

- 版心 `--container-max: 1120px`；导航高 `--header-height: 72px`
- 间距阶梯 `--space-1..9`（4→96px），营销页区块 padding ≥64px，区块间严格 `--bg-page` / `--bg-mist` 交替
- 卡片：`--radius-card: 20px`、1px `--line` 边、`--shadow-subtle`；悬停 `--shadow-hover` + translateY(-2px)
- 控件圆角 `--radius-control: 14px`；按钮胶囊 `--radius-pill`
- 断点收敛为两档：**768px**（移动/桌面分界）与 **1024px**（宽屏）；新代码禁引入其他断点
- 每屏一个主任务；首屏主 CTA 唯一

## 5. 组件规范（状态完备性：default/hover/focus-visible/active/disabled/loading/empty 全配齐）

- **按钮**（`.nur-btn`）：主按钮黛绿+`box-shadow: 0 8px 20px rgba(30,77,62,.22)`；hover 上移 1px；active scale(.97) 或下沉 1px；focus-visible 2px 黛绿环 + 3px 偏移；次按钮 1px `--line` 描边半透明白底
- **涟漪**：主操作按钮可加 `.nur-ripple-host` + 全局涟漪指令，380ms，仅 transform/opacity
- **卡片**：素笺白底；禁重阴影堆叠
- **表单**：1.5px `--line` 边、focus 时边框转黛绿 + 2px soft 底光；错误态色边 + 文字说明，禁刺眼红框闪烁
- **空状态**：一句说明 + 一个明确下一步操作按钮
- **Loading**：骨架屏或按钮文案切换（如「提交中…」），禁整页白屏
- **图标**：线性 1.5px stroke（Lucide 风格内联 SVG），24px

## 6. 动效（全部仅 transform/opacity/filter）

- 入场：淡入 + 上移 8–12px，240–400ms，`--ease: cubic-bezier(0.2,0,0,1)`，阶梯错落 60–80ms（`--stagger-step`）
- Hero 级「雾散」：blur(12px)→清晰，≤600ms，仅首屏一次
- `prefers-reduced-motion`：全局压缩至 0.01ms（base.css 已有），**内容默认可见**——任何元素不得以 `opacity:0` 为无动画时的静止态（hero-rise 假消失历史陷阱）
- 页面离屏停止轮询/动画；无自动轮播

## 7. ADHD 友好硬性规则

1. 每页/每屏回答三问：我在哪、现在做什么、下一步点哪
2. 多步操作必须步进式（Wizard）呈现，带进度感；表单支持草稿/错误恢复
3. 所有写操作：幂等键由拦截器自动注入，组件只需处理 loading→success/error 的完整反馈环
4. 错误提示说「出了什么问题 + 下一步怎么办」
5. 触控热区 ≥44px；键盘可达；focus 可见

## 8. 页面骨架模板

**营销页**（landing/teachers/detail）：`site-header`（72px，首页首屏透明→滚动 40px 后雾白 blur(12px)+1px 底线）→ Hero → 交替区块 → 结尾 CTA（雾青底+漏窗小图，禁深色）→ `site-footer`（浅色）
**工作台**（student/parent/teacher/admin）：浅色密排，顶栏 + 卡片栅格；今日行动卡置顶聚焦；次级信息折叠/分区 Tab
**弹层**：模态居中卡 + 雾白遮罩；危险操作两阶段（preview→execute）二次确认

## 9. 工程约束速查（违反即返工）

- 品牌锁：`node tools/verify_brand_css.cjs` 比对 landing/auth/match 三 CSS + brand-presentation.css 与基线提交的声明集；本轮重构获用户授权改品牌，**全部完成后由主代理统一更新锁基线提交**，实现代理不得删改该脚本
- 运行现场勿动：8080（后端）/4200（ng serve，自动热重载）；验证一律用 **http://localhost:4200**（127.0.0.1 会触发 CORS 403）
- 登录请求体 `{role, account, password}`，须先在门户切角色 tab；分流由后端返回 role 权威驱动
- 单组件样式预算 8kB 警告 / 16kB 失败（angular.json）；超了就拆文件或用全局类
- 禁改历史 Flyway 迁移；禁动资金结算逻辑；禁 git reset/clean/强推
- 构建验证：`cd frontend && npx ng build`（零警告零错误为通过）

## 10. 无 UI 功能缺口承接（重构时同步补齐入口）

A1 两阶段取消预约 / A2 开始·完成课程 / A3 我的预约列表（P0 交易闭环）
B1 学习画像 / B2 学生资料 / B3 关闭答疑 / B4 课堂记录 / B5 我的学生 / B6 课前任务生成 / B7 导师任务总览
C1 导师公开评价列表 / C2 导师收到评价
D1 平台参数配置 / D2 登录限流解锁
端点与落点详见 `audit/frontend-audit.md` §12.1。
