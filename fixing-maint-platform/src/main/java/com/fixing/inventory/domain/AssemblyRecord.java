package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组装记录：一次"配件 → 整机"的生产动作（谁装的/装了几台/什么机型）。
 * 消耗的配件明细在 assembly_part —— 想统计先记细，组装成本从这里算。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assembly_record")
public class AssemblyRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long machineStockId;

    /** 本次组装台数 */
    private Integer qty;

    private String remark;
}
