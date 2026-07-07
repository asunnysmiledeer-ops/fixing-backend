package com.fixing.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 通用动作请求体（接单/完工/确认/驳回/取消共用）。
 * laborFee 仅"完工"动作用：不在保工单的维修费报价（不填用系统默认标准）。
 */
@Data
public class TicketActionDTO {

    /** 备注：完工说明 / 驳回原因 / 取消原因…… 写进流转日志 */
    private String remark;

    /** 完工专用：本单维修费（元）。不在保工单生效；不填按默认标准 */
    private BigDecimal laborFee;
}
