@echo off
chcp 65001 >nul
title NURTURE 测试环境启动器
echo ================================================
echo    润物细无声 NURTURE - 测试环境一键启动
echo ================================================
echo.
echo 使用前提：Docker Desktop 已经打开并显示运行中。
echo.
pause
echo [1/3] 启动数据库...
start "" "%~dp0启动-数据库.bat"
timeout /t 3 /nobreak >nul
echo [2/3] 启动后端（新窗口，等出现 Started NurtureApplication）...
start "" "%~dp0启动-后端.bat"
timeout /t 3 /nobreak >nul
echo [3/3] 启动前端（新窗口，等出现 Local: 4200）...
start "" "%~dp0启动-前端.bat"
echo.
echo ================================================
echo  全部启动大约需要 40-60 秒。
echo  然后用浏览器打开： http://localhost:4200
echo  运营测试账号：13800000000 / Admin#2026（仅本机测试用）
echo ================================================
pause
