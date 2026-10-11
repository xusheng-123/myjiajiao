// Read-only comparison against the accepted brand baseline; requires installed frontend dependencies.
// 基线更新记录：096e474（阶段7 竹林暗色收口）→ 38682ba（2026-10-11 方向 D「黛绿暖金」全站重构，用户授权品牌变更）。
// 今后对 landing/auth/match 三个组件 CSS 与 brand-presentation.css 的改动仍须保持与本基线集合等价（纯增量）。
const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const postcss = require(path.join(root, 'frontend/node_modules/postcss'));
const baseline = process.argv[2] || '38682ba';
const files = ['auth/auth-portal.component.css', 'landing/landing.component.css', 'match/match.component.css']
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
