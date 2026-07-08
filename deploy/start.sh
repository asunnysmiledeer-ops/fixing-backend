#!/usr/bin/env bash
# ============================================================
# FIX-ING 维保平台 · 启动脚本（Linux / macOS）
# 用法：改好下面几个变量的值，然后运行  bash start.sh
# ============================================================

# ── 按你的实际情况修改这几项 ─────────────────────────────
export DB_PASSWORD='1234'          # 【必改】你的 MySQL 密码
export JWT_SECRET='CHANGE-ME-to-a-random-string-at-least-32-chars-0123456789'  # 【上线必改】登录令牌密钥，随机长串≥32位

# ── 一般不用改（换机器/端口时才改）───────────────────────
export DB_URL='jdbc:mysql://localhost:3306/fixing?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='root'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
# export REDIS_PASSWORD=''         # Redis 有密码才取消注释填写
# export SERVER_PORT='8080'        # 换监听端口才取消注释
# ─────────────────────────────────────────────────────────

JAR="$(dirname "$0")/fixing-admin-0.3.0.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "❌ 没找到 java，请先安装 JDK 17+（见部署教程第二步）"; exit 1
fi
if [ ! -f "$JAR" ]; then
  echo "❌ 没找到 $JAR，请确认 jar 和本脚本在同一目录"; exit 1
fi

echo "🚀 正在启动 FIX-ING… 访问 http://localhost:${SERVER_PORT:-8080}"
exec java -jar "$JAR"
