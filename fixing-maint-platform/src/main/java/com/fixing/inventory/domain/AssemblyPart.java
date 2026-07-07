package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 组装消耗明细：这次组装用了哪些配件×多少（含单价快照，算组装成本）。 */
@Data
@TableName("assembly_part")
public class AssemblyPart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assemblyId;
    private Long partId;
    private Integer qty;
    private java.math.BigDecimal unitPrice;
}
