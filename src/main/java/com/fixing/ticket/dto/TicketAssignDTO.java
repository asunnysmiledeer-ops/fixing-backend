package com.fixing.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 派单/改派请求体（UC5/UC6）。操作人（必须是管理员）从登录态取，
 * 请求体只需要"派给谁"。
 */
@Data
public class TicketAssignDTO {

    /** 派给哪个工程师（sys_user.id，角色必须是 ENGINEER） */
    @NotNull(message = "工程师不能为空")
    private Long engineerId;

    /** 备注/改派原因 */
    private String remark;
}
