package com.fixing.ticket.priority;

import com.fixing.ticket.dto.TicketCreateDTO;
import com.fixing.ticket.enums.Priority;

/**
 * 优先级判定接口 ——【设计心法：给易变决策留一扇门】。
 *
 * <p>"怎么判优先级"是易变的：现在用关键词规则，将来可能换成 AI（M13）。
 * 把它抽象成接口后，TicketService 只依赖这个接口，换实现（规则→AI）时
 * 业务流程一行不改，只需新增一个实现类并调整注入。
 *
 * <p>同样的一扇门将来还有 Dispatcher（派单/重派建议），v0 派单是纯手动，先不建。
 */
public interface PriorityDecider {

    /**
     * 根据报修内容判定优先级。
     *
     * @param dto 报修请求（标题/描述/类型都可以作为判定依据）
     * @return P0–P4
     */
    Priority decide(TicketCreateDTO dto);
}
