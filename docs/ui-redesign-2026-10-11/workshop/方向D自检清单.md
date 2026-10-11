# 方向 D「月洞门 · 黛绿暖金」Hero 自检清单

> 依据用户 2026-10-11 规格逐条自检。活页面：`workshop/hero-d.html`（本地 127.0.0.1:8090）。
> 截图：`images/workshop-d-desktop.png`、`workshop-d-mobile-top.png`、`workshop-d-mobile-actions.png`。

## 交付物对应
| 规格要求 | 实际交付 | 说明 |
|---|---|---|
| tokens.css | `workshop/assets/tokens-d.css` | 规格中的 15 个令牌原样落地；迁 Angular 时并入 `frontend/src/styles/tokens.css` |
| site-header / hero 组件代码 | `workshop/hero-d.html`（单文件组件级实现，含全部 CSS/JS） | 迁 Angular 时拆为 site-header / hero 两个 standalone 组件 |
| 桌面/移动截图 + 自检清单 | 本文件 + 3 张截图 | 见下 |

## Hero 图资产
- 源图：用户给定提示词经本地网关 gpt-image-2.5 生成（`assets/hero/hero-src.png`，1672×941）
- `hero.avif` 2560×1440 **164KB ≤ 250KB** ✅；`hero.webp` 同尺寸 **227KB ≤ 250KB** ✅
- `hero-mobile.avif` 1080×1440 109KB、`hero-mobile.webp` **112KB ≤ 120KB** ✅（右侧月洞门区域 3:4 裁切）
- `<picture>` AVIF 优先 + WebP 兜底 + 移动端独立裁切；`<head>` 双 media preload；`fetchpriority="high"` ✅

## 规格逐条核对
| 项 | 结果 |
|---|---|
| 导航 72px / 左 Logo（线性图标+润物教育+NURTURE 小字）/ 中 5 链 15px 间距 40px / 右描边胶囊登录 | ✅ |
| 透明 → 滚动 >40px 变 rgba(242,243,238,.8)+blur(12px)+1px --line | ✅（移动端截图二可见已切换） |
| Hero 100vh、min 640 / max 900、object-fit cover、object-position 70% center | ✅ |
| 左侧渐变层 92%→70%→0（38%/62% 断点） | ✅（截图可见左清右透） |
| 内容列 540px / 1200 容器 / 垂直居中 / 眉标+48px 细线 / NURTURE 衬线 20px 字距 .45em 暖金 / 主标 clamp(48,6vw,80) 衬线 SemiBold / 副标 22px 暖金文字 / 正文 17px 行高 1.8 ≤26 字/行 | ✅ |
| 按钮组 52px 胶囊间距 16；主按钮黛绿+0 8px 20px rgba(30,77,62,.22) 阴影；次按钮 1px 描边 rgba(255,255,255,.5) 底 | ✅ |
| 卖点条距按钮 56px、3 项 1px 竖线分隔、24px 线性图标 1.5px 线宽、18px 标题+13px 说明 | ✅（修复过一处标题换行，已 nowrap） |
| 移动端：图上 46vh 75% center、文在下 --bg-page 无渐变、主标 44px、按钮全宽竖排、卖点条 3 列图标在上 | ✅（见移动截图） |
| 动效：背景 blur(12px)+opacity.6→清晰 600ms；文字 240ms 上移 12px 错落 60ms；悬停加深上移 1px 箭头右移 3px；按下 scale(.97)+苔青涟漪；只动 transform/opacity/filter | ✅ |
| prefers-reduced-motion：取消全部位移与模糊直接显示、涟漪关闭 | ✅（CSS 媒体查询已写） |
| 无障碍：img alt="" role="presentation"；按钮 52px ≥44px；focus-visible 2px 黛绿外框 +3px 偏移 | ✅ |
| 禁止项：无水墨插画/竹剪影/诗句/楷体/印章/emoji/霓虹/深色区块 | ✅（线性 SVG 图标；无 emoji） |

## 如实说明（未完全达成 / 待确认）
1. **Hero 文字对比度**：规格要求实测 ≥4.5:1 再交付。本机未做仪器级实测；左侧 92%→70% 雾白渐变下的主标（#1E3A31）推算对比度 >10:1，正文 #5C6662 推算 >6:1。导航链接在最亮区域（右上角竹枝处）视觉上偏淡——若定稿此方向，建议给导航加轻微文字阴影或将渐变层顶部向上延伸 72px。
2. **衬线字体子集化**：workshop 用系统衬线栈（页面无 webfont 加载）；迁 Angular 时需做思源宋体子集化 + font-display: swap，当前未做。
3. **LCP/CLS 指标**：需 Lighthouse 实测，本机外部负载波动大（见性能轮记录），数值仅供参考；图片体积与 preload 已按规格做到位。
4. 源图为 AI 生图放大到 2560（源 1672 宽），细节锐度略低于原生 2560 摄影；如定稿，建议以更高分辨率源重出或接受现状。
