@echo off
chcp 65001 >nul
title NURTURE - 前端服务（不要关闭此窗口）
cd /d "E:\线上家教平台\线上家教平台（功能）\frontend"
echo 正在启动前端，请等待出现 Local: http://localhost:4200 字样...
echo （如果提示端口被占用，说明前端已在运行，直接打开浏览器即可）
npm start -- --host 0.0.0.0
pause
