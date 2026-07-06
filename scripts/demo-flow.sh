#!/usr/bin/env bash
# ============================================================
# FIX-ING Demo v0 · 一条工单打穿全流程演示脚本（G 阶段）
# 前提：应用已在 8080 端口启动、seed.sql 已灌入
# 演示链：报修 → 派单 → 接单 → 换件扣库存 → 完工 → 驳回 → 返工 → 确认
# 中途穿插三类"应被拒绝"的反面用例（非法跳转/越权/库存不足）
# ============================================================
set -e
B=${1:-http://localhost:8080}
post() { curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2"; echo; }

echo "▶ 0. 健康检查"; curl -s $B/ping; echo; echo

echo "▶ 1. 客户(id=1)报修：描述含'全院'→规则判 P0"
post /tickets '{"operatorId":1,"customerId":1,"equipmentId":1,"type":"HARDWARE","title":"叫号主机黑屏","description":"全院叫号停摆","contactName":"王信息","contactPhone":"13800000001"}'

TICKET_ID=$(curl -s "$B/tickets?status=PENDING_ASSIGN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['id'])")
echo "  → 新工单 id=$TICKET_ID"; echo

echo "▶ 2.【反例】未派单就接单 → 应被拒"
post /tickets/$TICKET_ID/accept '{"operatorId":3}'; echo

echo "▶ 3.【反例】客户越权派单 → 应被拒"
post /tickets/$TICKET_ID/assign '{"operatorId":1,"engineerId":3}'; echo

echo "▶ 4. 管理员(id=2)派单给工程师(id=3)"
post /tickets/$TICKET_ID/assign '{"operatorId":2,"engineerId":3}'; echo

echo "▶ 5. 工程师接单 → 处理中"
post /tickets/$TICKET_ID/accept '{"operatorId":3}'; echo

echo "▶ 6. 换件：打印头 x2（库存 10→8，写领料流水）"
post /tickets/$TICKET_ID/use-part '{"operatorId":3,"partId":1,"qty":2}'; echo

echo "▶ 7.【反例】库存不足：液晶屏 x99 → 应被拒且不产生流水"
post /tickets/$TICKET_ID/use-part '{"operatorId":3,"partId":2,"qty":99}'; echo

echo "▶ 8. 工程师完工 → 待确认"
post /tickets/$TICKET_ID/complete '{"operatorId":3,"remark":"更换打印头，测试正常"}'; echo

echo "▶ 9. 客户驳回（没修好）→ 打回处理中"
post /tickets/$TICKET_ID/reject '{"operatorId":1,"remark":"屏幕还是不亮"}'; echo

echo "▶ 10. 返工完工 + 客户确认 → 已完成(终态)"
post /tickets/$TICKET_ID/complete '{"operatorId":3,"remark":"HDMI线松动已固定"}' > /dev/null
post /tickets/$TICKET_ID/confirm '{"operatorId":1}'; echo

echo "▶ 11.【反例】对已完成工单接单 → 应被拒"
post /tickets/$TICKET_ID/accept '{"operatorId":3}'; echo

echo "▶ 12. 工单完整时间线（审计日志）"
curl -s $B/tickets/$TICKET_ID/logs | python3 -m json.tool
echo
echo "▶ 13. 该工单领料流水 + 当前库存"
curl -s "$B/part-usages?ticketId=$TICKET_ID" | python3 -m json.tool
curl -s $B/spare-parts | python3 -m json.tool
echo "✅ 演示结束：一条工单走完六步 + 中途换件扣库存，全程可追溯"
