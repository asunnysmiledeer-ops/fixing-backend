@echo off
REM ============================================================
REM  FIX-ING 维保平台 · 启动脚本（Windows）
REM  用法：改好下面几行的值，双击本文件 或 命令行运行 start.bat
REM ============================================================

REM ── 按你的实际情况修改这几项 ──
set DB_PASSWORD=1234
set JWT_SECRET=CHANGE-ME-to-a-random-string-at-least-32-chars-0123456789

REM ── 一般不用改（换机器/端口时才改）──
set DB_URL=jdbc:mysql://localhost:3306/fixing?useUnicode=true^&characterEncoding=utf8^&serverTimezone=Asia/Shanghai
set DB_USERNAME=root
set REDIS_HOST=localhost
set REDIS_PORT=6379
REM set REDIS_PASSWORD=
REM set SERVER_PORT=8080

where java >nul 2>nul
if errorlevel 1 (
  echo [X] 没找到 java，请先安装 JDK 17+（见部署教程第二步）
  pause & exit /b 1
)
if not exist "%~dp0fixing-admin-0.3.0.jar" (
  echo [X] 没找到 fixing-admin-0.3.0.jar，请确认 jar 和本脚本在同一目录
  pause & exit /b 1
)

echo 正在启动 FIX-ING... 访问 http://localhost:8080
java -jar "%~dp0fixing-admin-0.3.0.jar"
pause
