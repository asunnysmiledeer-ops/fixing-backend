package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 领料记录（流水表）。心法【想统计先记细】不变，M1 新增计费快照：
 * billable=该次换件是否计费（合同免费件=false）；unitPrice=领用当时的单价快照
 * （备件将来调价不影响历史账 —— 金额相关的历史必须存快照，不能存引用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spare_part_usage")
public class SparePartUsage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partId;
    private Integer qty;
    private Long engineerId;
    private Long ticketId;
    private Long equipmentId;

    /** 是否计费：在保且备件在合同免费清单内=false，其余=true */
    private Boolean billable;

    /** 领用时的单价快照 */
    private BigDecimal unitPrice;
}
