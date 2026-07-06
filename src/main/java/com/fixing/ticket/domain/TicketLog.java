package com.fixing.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.user.domain.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录，对应表 ticket_log。
 *
 * <p>作用：审计 + 进度展示。每次状态变更（以及换件这类关键动作）都追加一条，
 * "谁在什么时候把工单从 A 状态改成了 B 状态、为什么"。
 * 客户端查进度、事后追责，都查这张表 —— 只追加、永不修改。
 */
@Data
@TableName("ticket_log")
public class TicketLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 属于哪张工单（外键 → ticket.id） */
    private Long ticketId;

    /** 变更前状态（新建时为 null） */
    private TicketStatus fromStatus;

    /** 变更后状态 */
    private TicketStatus toStatus;

    /** 动作名：create/assign/accept/complete/confirm/reject/cancel/reassign/use_part */
    private String action;

    /** 操作人（外键 → sys_user.id） */
    private Long operatorId;

    /** 操作人当时的角色（冗余存，避免以后改角色影响历史记录的可读性） */
    private UserRole operatorRole;

    /** 备注：改派原因、驳回原因、换了什么件…… */
    private String remark;

    private LocalDateTime createdAt;
}
