@echo off
chcp 65001 >nul
title NURTURE - 后端服务（不要关闭此窗口）
cd /d "E:\线上家教平台\线上家教平台（功能）\backend"
set "JAVA_HOME=C:\Users\lxs\apps\jdk-25.0.4.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "NURTURE_ADMIN_PHONE=13800000000"
set "NURTURE_ADMIN_PASSWORD=Admin#2026"
echo 正在启动后端，请等待出现 Started NurtureApplication 字样...
echo （首次启动约 10-20 秒；出现后即可回到浏览器操作）
java -jar target\nurture-backend-1.0.0.jar
pause
