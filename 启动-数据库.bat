@echo off
chcp 65001 >nul
title NURTURE - 数据库
echo 正在启动数据库容器（需要 Docker Desktop 已在运行）...
docker start deploy-postgres-1
if errorlevel 1 (
  echo.
  echo 启动失败：请先打开 Docker Desktop（开始菜单里的鲸鱼图标），
  echo 等它显示 Running 后，重新双击本文件。
  pause
) else (
  echo 数据库已启动。本窗口可以最小化。
  pause
)
