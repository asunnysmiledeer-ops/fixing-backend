package com.fixing.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通用动作请求体：接单/完工/确认/驳回/取消 共用。
 * 动作本身由 URL 表达（POST /tickets/{id}/accept…），请求体只带"谁操作 + 备注"。
 */
@Data
public class TicketActionDTO {

    /** 操作人 id（v0 无登录，显式传；Service 会校验角色与身份） */
    @NotNull(message = "操作人不能为空")
    private Long operatorId;

    /** 备注：完工说明 / 驳回原因 / 取消原因…… 写进 ticket_log */
    private String remark;
}
