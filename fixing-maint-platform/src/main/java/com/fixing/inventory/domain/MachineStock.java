package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 整机库存（M1.5 订单域）：按机型记"可派发的完整机器"数量。
 * 入 = 组装（消耗配件）；出 = 订单派发（生成客户设备档案）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("machine_stock")
public class MachineStock extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备类型（与设备台账/备件适用类型同一口径） */
    private String equipmentType;

    /** 型号，唯一，如 QM-2000 */
    private String model;

    /** 现有整机数量 */
    private Integer qty;
}
