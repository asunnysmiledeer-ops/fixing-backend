package com.fixing.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.ticket.domain.Ticket;

/** 工单 Mapper：单表 CRUD 由 BaseMapper 提供，条件查询用 Wrapper 在 Service 拼。 */
public interface TicketMapper extends BaseMapper<Ticket> {
}
