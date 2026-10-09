/**
 * Angular 环境配置 — 生产/预发布模板。
 *
 * ⚠️ 本文件由 `npm run build:staging` / `npm run build:pages` 的 scripts/write-env.mjs
 * 在构建时按环境变量 NURTURE_API_HOST（如 api-staging.example.com）重写，
 * 经 angular.json production fileReplacements 生效。
 * 仓库内保持下方同源回退值（无任何硬编码域名）；真实域名只存在于构建产物。
 */
export const environment = {
  production: true,
  /** 构建时重写为 https://<NURTURE_API_HOST>/api；未注入时回退同源相对路径 */
  apiBaseUrl: '/api',
};
