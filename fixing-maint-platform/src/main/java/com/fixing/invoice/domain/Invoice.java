package com.fixing.invoice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票（应收）：开票(ISSUED) → 回款(PAID)。
 * 可选关联工单：按次收费的结算单转开发票时带上 ticket_id，账能对上单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("invoice")
public class Invoice extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invoiceNo;
    private Long customerId;
    private Long contractId;

    /** 按次维修的发票关联工单（年费票为空） */
    private Long ticketId;

    private String title;
    private BigDecimal amount;

    /** ISSUED 已开票 / PAID 已回款 */
    private String status;

    private LocalDate issuedAt;
    private LocalDate paidAt;
}
