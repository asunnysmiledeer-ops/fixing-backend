package com.fixing.invoice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票实体（M9 应收最小版），对应表 invoice。管理端专用：开票与回款跟踪。
 * v0.3 只做应收（向客户开票）；应付（采购款）留给后续。
 */
@Data
@TableName("invoice")
public class Invoice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票号（唯一），如 INV-2026-0001 */
    private String invoiceNo;

    /** 开给哪家客户 */
    private Long customerId;

    /** 关联合同（年费票）；合同外维修开票可为空 */
    private Long contractId;

    /** 开票项目，如 "2026年度维保费（下半年）" */
    private String title;

    /** 金额：一律 BigDecimal */
    private BigDecimal amount;

    /** ISSUED 已开票（待回款）/ PAID 已回款 */
    private String status;

    /** 开票日 / 回款日 */
    private LocalDate issuedAt;
    private LocalDate paidAt;

    private LocalDateTime createdAt;
}
