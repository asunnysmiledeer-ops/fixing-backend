package com.fixing.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 派单/改派请求体（UC5/UC6）：在通用动作字段之外，还要指定派给哪个工程师。
 */
@Data
public class TicketAssignDTO {

    /** 操作人（必须是管理员） */
    @NotNull(message = "操作人不能为空")
    private Long operatorId;

    /** 派给哪个工程师（sys_user.id，角色必须是 ENGINEER） */
    @NotNull(message = "工程师不能为空")
    private Long engineerId;

    /** 备注/改派原因 */
    private String remark;
}
