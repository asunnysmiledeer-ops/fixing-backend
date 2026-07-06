#!/usr/bin/env bash
# ============================================================
# FIX-ING Demo · 一条工单打穿全流程演示脚本（v0.2 带登录版）
# 前提：应用已在 8080 端口启动、seed.sql 已灌入
# 流程：三个角色分别登录拿 JWT → 报修 → 派单 → 接单 → 换件 →
#       完工 → 驳回 → 返工 → 确认；穿插"应被拒绝"的反面用例
# ============================================================
set -e
B=${1:-http://localhost:8080}

# 登录拿 token：POST /auth/login → data.token
login() {
  curl -s -X POST "$B/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"123456\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])"
}

# 带令牌请求：post <token> <path> <json-body>
post() { curl -s -X POST "$B$2" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$3"; echo; }
get()  { curl -s "$B$2" -H "Authorization: Bearer $1"; }

echo "▶ 0. 健康检查"; curl -s $B/ping; echo; echo

echo "▶ 0.1 不带令牌调接口 →【反例】应 401"
curl -s -o /dev/null -w "HTTP %{http_code}\n" $B/tickets; echo

echo "▶ 0.2 三个角色登录拿 JWT"
T_CUST=$(login hospital_it); T_ADMIN=$(login admin); T_ENG=$(login engineer_zh)
echo "  客户/管理员/工程师令牌已签发（各 ${#T_CUST} 字符）"; echo

echo "▶ 1. 客户报修：描述含'全院'→规则判 P0（注意请求体里没有 operatorId，身份来自令牌）"
post $T_CUST /tickets '{"customerId":1,"equipmentId":1,"type":"HARDWARE","title":"叫号主机黑屏","description":"全院叫号停摆","contactName":"王信息","contactPhone":"13800000001"}'

TICKET_ID=$(get $T_ADMIN "/tickets?status=PENDING_ASSIGN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['id'])")
echo "  → 新工单 id=$TICKET_ID"; echo

echo "▶ 2.【反例】客户越权派单 → 应被拒"
post $T_CUST /tickets/$TICKET_ID/assign '{"engineerId":3}'; echo

echo "▶ 3. 管理员派单给工程师"
post $T_ADMIN /tickets/$TICKET_ID/assign '{"engineerId":3}'; echo

echo "▶ 4. 工程师接单 → 处理中"
post $T_ENG /tickets/$TICKET_ID/accept '{}'; echo

echo "▶ 5. 换件：打印头 x2（扣库存 + 领料流水，记到令牌对应的工程师头上）"
post $T_ENG /tickets/$TICKET_ID/use-part '{"partId":1,"qty":2}'; echo

echo "▶ 6.【反例】库存不足：液晶屏 x99 → 应被拒"
post $T_ENG /tickets/$TICKET_ID/use-part '{"partId":2,"qty":99}'; echo

echo "▶ 7. 完工 → 客户驳回 → 返工完工 → 客户确认"
post $T_ENG  /tickets/$TICKET_ID/complete '{"remark":"更换打印头，测试正常"}' > /dev/null
post $T_CUST /tickets/$TICKET_ID/reject   '{"remark":"屏幕还是不亮"}' > /dev/null
post $T_ENG  /tickets/$TICKET_ID/complete '{"remark":"HDMI线松动已固定"}' > /dev/null
post $T_CUST /tickets/$TICKET_ID/confirm  '{}'; echo

echo "▶ 8.【反例】对已完成工单接单 → 应被拒"
post $T_ENG /tickets/$TICKET_ID/accept '{}'; echo

echo "▶ 9. 工单完整时间线（审计日志）"
get $T_ADMIN /tickets/$TICKET_ID/logs | python3 -m json.tool; echo

echo "✅ 演示结束：登录鉴权 + 一条工单六步 + 换件扣库存，全程可追溯"
