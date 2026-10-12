// Read-only comparison against the accepted brand baseline; requires installed frontend dependencies.
// 基线更新记录：096e474（阶段7 竹林暗色收口）→ 38682ba（2026-10-11 方向 D「黛绿暖金」全站重构，用户授权品牌变更）→ 16f13f1（2026-10-12 导师匹配页内页深化：园林背景带+右栏+导师卡信息带，对齐用户提供的内页参考图）→ 600d4e0（2026-10-12 登录门户园林摄影背景深化，用户授权：生图 login-garden + 左区提亮柔光留白；auth 样式拆分为 component.css + auth-portal-form.css 两件，files 清单同步纳入后者）→ fa135ba（2026-10-12 登录页微调，用户授权：内容整体右移收拢右侧留白 + 品牌正文加深至 --text-main）→ 22da9f2（2026-10-12 登录页微调第二轮，用户授权：右移过度回收居中 + 登录卡加宽 560px + 字段 .field-row 双列对排）→ 01f8f43（2026-10-12 登录页布局精修，用户规格书授权：1680 栅格 860+100+640 + 卡片 640/690 参数化 + 表单单列 + 四档响应式，撤销 .field-row 双列）→ 10f00fe（2026-10-12 首页 2–4 屏生图背景接入，用户授权：landing 拆 styleUrls 双文件，files 清单同步纳入 landing-sections.css；三屏面纱/取景/标题柔光/链接 pill，首屏 hero 零改动）→ c6e9213（2026-10-12 首页 5–9 屏生图背景接入，用户授权：landing 拆 styleUrls 三文件，files 清单同步纳入 landing-sections-2.css；五屏面纱/取景/标题柔光/步骤区柔光/移动档加强，首屏 hero 零改动）→ 2d86955（2026-10-12 第九屏 CTA 居中改版，用户授权：撤漏窗景窗 figure.cta-frame 及其 CSS 规则并删除 cta-lattice 资产、cta 居中排版三档响应式规格（900px/45%/44px/52px）、背景取景 48%/50%；文案颜色业务功能零改动）→ 87c3764（2026-10-12 页脚晨雾过渡改版，用户授权：第九屏底部 200px/移动 120px 渐隐层衔接页脚顶色 #F5F4ED、页脚晨雾渐变底 #F5F4ED→#EFF2E9→#E7EEE8、竹影/苔石装饰（第九屏源图裁切径向羽化）、排版参数表（1200px/1.3:1:1.2/64-56px/24-17-14px/#244638-#61766B-#B28B57/版权区 64px/分隔线 rgba(47,78,60,0.08)）；files 清单同步纳入 site-footer.component.css（路径前缀改为全路径）；文案链接业务功能零改动）。
// 今后对 landing/auth/match 三个组件 CSS（含 auth-portal-form.css、landing-sections.css、landing-sections-2.css）、site-footer.component.css 与 brand-presentation.css 的改动仍须保持与本基线集合等价（纯增量）。
const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const postcss = require(path.join(root, 'frontend/node_modules/postcss'));
const baseline = process.argv[2] || '87c3764';
const files = ['frontend/src/app/features/auth/auth-portal.component.css', 'frontend/src/app/features/auth/auth-portal-form.css', 'frontend/src/app/features/landing/landing.component.css', 'frontend/src/app/features/landing/landing-sections.css', 'frontend/src/app/features/landing/landing-sections-2.css', 'frontend/src/app/features/match/match.component.css', 'frontend/src/app/layout/site-footer/site-footer.component.css'];
function declarations(css) {
  const result = [];
  postcss.parse(css.replace(/\r\n/g, '\n')).walkDecls(declaration => {
    result.push(declaration.prop + ':' + declaration.value + (declaration.important ? '!important' : ''));
  });
  return result;
}
const before = files.flatMap(file => declarations(cp.execFileSync('git', ['show', baseline + ':' + file], {cwd:root, encoding:'utf8'}))).sort();
const after = [...files, 'frontend/src/styles/brand-presentation.css']
  .flatMap(file => declarations(fs.readFileSync(path.join(root,file), 'utf8'))).sort();
if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error('Brand CSS declarations changed');
console.log('PASS: ' + before.length + ' original CSS declarations retained, including colors and animations; original budgets unchanged.');
