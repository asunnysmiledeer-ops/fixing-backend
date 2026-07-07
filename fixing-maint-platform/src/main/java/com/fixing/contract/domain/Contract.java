package com.fixing.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 维保合同。M1 起为"颗粒化绑定"：合同保什么由三张明细表决定 ——
 * contract_equipment(保哪几台) / contract_part(哪些备件免费换) / contract_software(保哪些软件)。
 * "在保"判定 = 设备出现在某份 ACTIVE 且未到期合同的设备明细里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract")
public class Contract extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    private String name;
    private String scope;
    private LocalDate startDate;
    private LocalDate endDate;

    /** YEARLY 年费 / PER_CASE 按次 / MIXED 混合 */
    private String billingType;

    private BigDecimal amount;

    /** ACTIVE / TERMINATED */
    private String status;
}
