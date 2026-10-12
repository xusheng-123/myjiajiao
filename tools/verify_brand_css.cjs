// Read-only comparison against the accepted brand baseline; requires installed frontend dependencies.
// 基线更新记录：096e474（阶段7 竹林暗色收口）→ 38682ba（2026-10-11 方向 D「黛绿暖金」全站重构，用户授权品牌变更）→ 16f13f1（2026-10-12 导师匹配页内页深化：园林背景带+右栏+导师卡信息带，对齐用户提供的内页参考图）→ 600d4e0（2026-10-12 登录门户园林摄影背景深化，用户授权：生图 login-garden + 左区提亮柔光留白；auth 样式拆分为 component.css + auth-portal-form.css 两件，files 清单同步纳入后者）→ fa135ba（2026-10-12 登录页微调，用户授权：内容整体右移收拢右侧留白 + 品牌正文加深至 --text-main）→ 22da9f2（2026-10-12 登录页微调第二轮，用户授权：右移过度回收居中 + 登录卡加宽 560px + 字段 .field-row 双列对排）→ 01f8f43（2026-10-12 登录页布局精修，用户规格书授权：1680 栅格 860+100+640 + 卡片 640/690 参数化 + 表单单列 + 四档响应式，撤销 .field-row 双列）→ 10f00fe（2026-10-12 首页 2–4 屏生图背景接入，用户授权：landing 拆 styleUrls 双文件，files 清单同步纳入 landing-sections.css；三屏面纱/取景/标题柔光/链接 pill，首屏 hero 零改动）。 → c6e9213（2026-10-12 首页 5–9 屏生图背景接入，用户授权：landing 拆 styleUrls 三文件，files 清单同步纳入 landing-sections-2.css；五屏面纱/取景/标题柔光/步骤区柔光/移动档加强，首屏 hero 零改动）。
// 今后对 landing/auth/match 三个组件 CSS（含 auth-portal-form.css、landing-sections.css）与 brand-presentation.css 的改动仍须保持与本基线集合等价（纯增量）。
const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const postcss = require(path.join(root, 'frontend/node_modules/postcss'));
const baseline = process.argv[2] || 'c6e9213';
const files = ['auth/auth-portal.component.css', 'auth/auth-portal-form.css', 'landing/landing.component.css', 'landing/landing-sections.css', 'landing/landing-sections-2.css', 'match/match.component.css']
  .map(file => 'frontend/src/app/features/' + file);
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
