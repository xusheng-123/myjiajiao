#!/usr/bin/env bash
# ============================================================
# 润物细无声 · NURTURE — Staging 部署包打包器（STAGING 收口轮）
# 用法：bash deploy/package-staging.sh
# 产出：deploy/dist/nurture-staging-package-<时间戳>.zip
#   ├─ backend/nurture-backend-1.0.0.jar   （后端可执行 jar，当前源码含 V1–V22 迁移；打包前核实构建）
#   ├─ deploy/…                             （compose 三层 + env 模板 + 备份/恢复/门禁脚本 + RUNBOOK）
#   ├─ frontend-src.txt                     （前端构建说明：Pages 以 NURTURE_API_HOST 从源码构建）
#   └─ MANIFEST.txt                         （包清单与校验信息）
# 纪律：前端静态产物不入包——必须经 Cloudflare Pages 构建变量注入真实 API 主机名。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
PKG_DIR="$ROOT/deploy/dist/nurture-staging-package-$STAMP"
JAR="$ROOT/backend/target/nurture-backend-1.0.0.jar"

[ -f "$JAR" ] || { echo "错误：未找到后端 jar（先执行 mvn package）: $JAR"; exit 1; }

mkdir -p "$PKG_DIR/backend"
cp "$JAR" "$PKG_DIR/backend/"
mkdir -p "$PKG_DIR/deploy"
cp "$ROOT"/deploy/docker-compose*.yml "$ROOT"/deploy/env.*.example \
   "$ROOT"/deploy/backup.sh "$ROOT"/deploy/restore.sh \
   "$ROOT"/deploy/verify_config.py "$ROOT"/deploy/RUNBOOK.md "$PKG_DIR/deploy/"

cat > "$PKG_DIR/frontend-src.txt" << 'EOF'
前端不随部署包发布。执行流程：
1) Cloudflare Pages 连接本仓库 frontend 目录
2) 构建命令：npm run build:pages
3) 构建变量：NURTURE_API_HOST=api-staging.<域名>   （生产为 api.<域名>）
4) 输出目录：dist/nurture-frontend/browser
5) 自定义域绑定 www-staging.<域名>
SPA 回退由 public/_redirects 保证（/* → /index.html 200）。
EOF

{
  echo "NURTURE Staging 部署包 @ $STAMP"
  echo "jar: $(sha256sum "$PKG_DIR/backend/nurture-backend-1.0.0.jar" | cut -d' ' -f1)"
  echo "内容物：backend jar（迁移版本须按实际构建核实，当前源码V1-V22）/ deploy 编排与脚本 / RUNBOOK / 前端构建说明"
  echo "执行顺序：deploy/RUNBOOK.md 第 7 节（17 步 Checklist）"
} > "$PKG_DIR/MANIFEST.txt"

cd "$PKG_DIR/.." && zip -qr "nurture-staging-package-$STAMP.zip" "nurture-staging-package-$STAMP"
echo "打包完成: deploy/dist/nurture-staging-package-$STAMP.zip"
ls -la "nurture-staging-package-$STAMP.zip"
