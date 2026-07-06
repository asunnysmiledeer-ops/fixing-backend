package com.fixing.ticket.dto;

import lombok.Data;

/**
 * 通用动作请求体：接单/完工/确认/驳回/取消 共用。
 * 动作本身由 URL 表达（POST /tickets/{id}/accept…）。
 *
 * <p>v0.2 起操作人从登录态（UserContext）取，请求体只剩备注 ——
 * 客户端再也不能"替别人操作"。
 */
@Data
public class TicketActionDTO {

    /** 备注：完工说明 / 驳回原因 / 取消原因…… 写进 ticket_log */
    private String remark;
}
