/**
 * Angular 环境配置 — 开发默认（同源相对路径，经 dev-server 代理到 localhost:8080）。
 * 生产/预发布由 `npm run build:pages` 中的 scripts/write-env.mjs 按
 * 构建变量 NURTURE_API_HOST 生成本文件的生产版本（fileReplacements 切换），
 * 域名不硬编码进源码。
 */
export const environment = {
  production: false,
  /** 同源相对路径：dev-server 代理 /api → http://localhost:8080 */
  apiBaseUrl: '/api',
};
