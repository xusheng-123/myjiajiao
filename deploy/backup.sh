#!/usr/bin/env bash
# ============================================================
# 润物细无声 · NURTURE — PostgreSQL 备份脚本（P0-1）
# 策略：每日全量 + 每周日归档；保留最近 7 天每日备份 + 最近 4 周每周备份
# 用法：./backup.sh
#   环境变量：
#     NURTURE_PG_CONTAINER  容器名（默认 deploy-postgres-1）
#     NURTURE_DB_NAME       库名（默认 nurture）
#     NURTURE_DB_USER       用户（默认 nurture）
#     NURTURE_BACKUP_DIR    备份根目录（默认 /var/backups/nurture；Windows 本机可用相对目录）
# 调度：Linux crontab「0 2 * * *」；Windows 任务计划程序每日 02:00
# ============================================================
set -euo pipefail

CONTAINER="${NURTURE_PG_CONTAINER:-deploy-postgres-1}"
DB_NAME="${NURTURE_DB_NAME:-nurture}"
DB_USER="${NURTURE_DB_USER:-nurture}"
BACKUP_ROOT="${NURTURE_BACKUP_DIR:-./backups}"

DAILY_DIR="$BACKUP_ROOT/daily"
WEEKLY_DIR="$BACKUP_ROOT/weekly"
LOG_FILE="$BACKUP_ROOT/backup.log"

mkdir -p "$DAILY_DIR" "$WEEKLY_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DOW="$(date +%u)"   # 1=周一 ... 7=周日
DUMP_FILE="$DAILY_DIR/${DB_NAME}-${TIMESTAMP}.dump"

log() { echo "[$(date '+%F %T')] $*" | tee -a "$LOG_FILE"; }

log "开始备份 container=$CONTAINER db=$DB_NAME -> $DUMP_FILE"
# -Fc 自定义格式（pg_restore 可并行/选择性恢复）；--no-owner 便于恢复到不同属主
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner > "$DUMP_FILE"

SIZE=$(du -h "$DUMP_FILE" | cut -f1)
log "备份完成 size=$SIZE"

# 数据完整性自检：pg_restore -l 列目录成功才算有效备份
if docker exec -i "$CONTAINER" pg_restore --list < "$DUMP_FILE" > /dev/null 2>&1; then
  log "备份完整性校验通过"
else
  log "警告：备份完整性校验失败，请立即检查！"
  exit 1
fi

# 每周日（DOW=7）同步归档一份周备份
if [ "$DOW" = "7" ]; then
  cp "$DUMP_FILE" "$WEEKLY_DIR/${DB_NAME}-week-${TIMESTAMP}.dump"
  log "已归档周备份"
fi

# 保留策略：每日备份保留 7 份（按文件名时间戳排序，超出删除）
ls -1t "$DAILY_DIR"/${DB_NAME}-*.dump 2>/dev/null | tail -n +8 | while read -r old; do
  rm -f "$old"; log "清理过期日备份：$old"
done
# 每周备份保留 4 份
ls -1t "$WEEKLY_DIR"/${DB_NAME}-week-*.dump 2>/dev/null | tail -n +5 | while read -r old; do
  rm -f "$old"; log "清理过期周备份：$old"
done

log "本轮备份任务结束"
