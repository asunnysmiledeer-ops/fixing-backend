package com.fixing.ticket.priority;

import com.fixing.ticket.dto.TicketCreateDTO;
import com.fixing.ticket.enums.Priority;
import org.springframework.stereotype.Component;

/**
 * 规则版优先级判定（v0 实现）：扫描标题+描述里的关键词。
 * 对应需求 §6：全院停摆=P0，全科室停摆=P1，多台受影响=P2，默认单台=P3，咨询=P4。
 *
 * <p>规则很糙没关系 —— 它的使命就是占住 PriorityDecider 这个位置，
 * 让整条业务链先跑起来；将来 AI 实现（M13）进场时直接替换它。
 */
@Component
public class RulePriorityDecider implements PriorityDecider {

    @Override
    public Priority decide(TicketCreateDTO dto) {
        // 标题 + 描述拼一起扫关键词（null 安全处理）
        String text = (dto.getTitle() == null ? "" : dto.getTitle())
                + (dto.getDescription() == null ? "" : dto.getDescription());

        if (text.contains("全院")) {
            return Priority.P0; // 全院停摆：最高优先级
        }
        if (text.contains("全科") || text.contains("科室停")) {
            return Priority.P1; // 整个科室停摆
        }
        if (text.contains("多台")) {
            return Priority.P2; // 多台设备受影响
        }
        if (text.contains("咨询") || text.contains("请教")) {
            return Priority.P4; // 非故障，一般咨询
        }
        return Priority.P3; // 默认：单台故障
    }
}
