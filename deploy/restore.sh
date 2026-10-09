#!/usr/bin/env bash
# ============================================================
# 润物细无声 · NURTURE — PostgreSQL 恢复脚本（P0-1）
# 用途：将 backup.sh 产出的 .dump 恢复到目标数据库（演练或灾备）
# 用法：./restore.sh <dump 文件> <目标库名>
#   示例：./restore.sh ./backups/daily/nurture-20260927-020000.dump nurture_restore_drill
#   环境变量同 backup.sh（NURTURE_PG_CONTAINER / NURTURE_DB_USER）
# 注意：目标库必须已存在（脚本自动创建）；恢复仅限演练库/灾备库，严禁在业务库上直接执行
# ============================================================
set -euo pipefail

CONTAINER="${NURTURE_PG_CONTAINER:-deploy-postgres-1}"
DB_USER="${NURTURE_DB_USER:-nurture}"

DUMP_FILE="${1:?用法: ./restore.sh <dump文件> <目标库名>}"
TARGET_DB="${2:?用法: ./restore.sh <dump文件> <目标库名>}"

[ -f "$DUMP_FILE" ] || { echo "错误：备份文件不存在: $DUMP_FILE"; exit 1; }

echo "[restore] 在容器 $CONTAINER 中创建目标库 $TARGET_DB（如不存在）"
docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -tc \
  "SELECT 1 FROM pg_database WHERE datname='$TARGET_DB'" | grep -q 1 || \
  docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE \"$TARGET_DB\" OWNER \"$DB_USER\""

echo "[restore] 恢复 $DUMP_FILE -> $TARGET_DB"
docker exec -i "$CONTAINER" pg_restore -U "$DB_USER" -d "$TARGET_DB" --no-owner --exit-on-error < "$DUMP_FILE"

echo "[restore] 校验：flyway_schema_history 最新版本与核心表行数"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" -c \
  "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" -tAc \
  "SELECT 'accounts=' || count(*) FROM accounts" 2>/dev/null || true

echo "[restore] 完成。启动后端指向该库验证："
echo "  NURTURE_DB_URL=jdbc:postgresql://localhost:5432/$TARGET_DB java -jar backend/target/nurture-backend-1.0.0.jar"
echo "  验证后删除演练库：docker exec $CONTAINER psql -U $DB_USER -d postgres -c \"DROP DATABASE \\\"$TARGET_DB\\\"\""
