package com.fixing.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.user.domain.UserRole;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单流转记录（审计流水，只追加不修改）。
 * operatorName 冗余存昵称：用户改名/删号后历史记录依然可读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_log")
public class TicketLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;

    /** create/assign/accept/complete/confirm/reject/cancel/reassign/use_part */
    private String action;

    private Long operatorId;
    private UserRole operatorRole;
    private String operatorName;
    private String remark;
}
