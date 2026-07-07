package com.fixing.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.ticket.domain.TicketLog;

/** 工单流转记录 Mapper：流水表，只 insert + select。 */
public interface TicketLogMapper extends BaseMapper<TicketLog> {
}
