package com.fixing.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工单结算明细（M1 新增）：不在保工单完工时系统自动生成。
 * 三种费目：VISIT 上门费(按标准) / PART 配件费(领料×单价，免费件剔除) / LABOR 维修费。
 * 汇总后可转开发票 —— 账款从"手工记账"变成"业务自动产生应收"。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_charge")
public class TicketCharge extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** VISIT 上门费 / PART 配件费 / LABOR 维修费 */
    private String itemType;

    /** 明细说明，如 "55寸液晶屏总成 ×1（不在保）" */
    private String itemName;

    private BigDecimal amount;
}
