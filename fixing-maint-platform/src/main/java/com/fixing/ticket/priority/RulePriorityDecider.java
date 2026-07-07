package com.fixing.ticket.priority;

import com.fixing.ticket.dto.TicketCreateDTO;
import com.fixing.ticket.enums.Priority;
import org.springframework.stereotype.Component;

/**
 * 规则版优先级判定（PriorityDecider 的 M1 实现）。
 * 服务申请类（添加机器/移机/装软件）没有故障 → 一律 P4 计划任务；
 * 维修类按影响范围关键词：全院 P0 > 全科 P1 > 多台 P2 > 默认单台 P3。
 * 将来 M13 换 AI 实现时，业务代码一行不改 —— 这扇门就是留给它的。
 */
@Component
public class RulePriorityDecider implements PriorityDecider {

    @Override
    public Priority decide(TicketCreateDTO dto) {
        if (dto.getType() != null && !dto.getType().isRepair()) {
            return Priority.P4; // 计划性服务，无紧急度
        }
        String text = (dto.getTitle() == null ? "" : dto.getTitle())
                + (dto.getDescription() == null ? "" : dto.getDescription());
        if (text.contains("全院")) {
            return Priority.P0;
        }
        if (text.contains("全科") || text.contains("科室停")) {
            return Priority.P1;
        }
        if (text.contains("多台")) {
            return Priority.P2;
        }
        if (text.contains("咨询") || text.contains("请教")) {
            return Priority.P4;
        }
        return Priority.P3;
    }
}
