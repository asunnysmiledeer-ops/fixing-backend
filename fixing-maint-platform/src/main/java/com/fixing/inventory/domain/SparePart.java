package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 备件实体。
 *
 * <p>【M1：库存随签约设备自动调整】备件通过 equipmentType 关联"适用设备类型"，
 * 系统统计该类型的**在保设备数 N**（出现在生效合同 contract_equipment 明细里的设备），
 * 动态预警阈值 = max(人工阈值, perDeviceQty × N)。客户签约设备变多 → 阈值自动上调。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spare_part")
public class SparePart extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** PART 配件 / COMPONENT 部件 / CONSUMABLE 耗材 */
    private String category;

    /** 适用设备类型（null=通用件，通用件只看人工阈值） */
    private String equipmentType;

    /** 每台在保设备建议备货数（动态阈值系数） */
    private Integer perDeviceQty;

    /** 当前库存。扣减必须走原子 SQL（SparePartMapper.deductStock） */
    private Integer stockQty;

    /** 人工阈值（动态阈值与它取较大者） */
    private Integer lowStockThreshold;

    /** 单价。金额一律 BigDecimal */
    private BigDecimal unitPrice;

    // ── 查询时计算的展示字段（exist=false：表里没有对应列）──

    /** 该类型"在保签约设备"台数 */
    @TableField(exist = false)
    private Integer contractedDeviceCount;

    /** 动态预警阈值 = max(lowStockThreshold, perDeviceQty × contractedDeviceCount) */
    @TableField(exist = false)
    private Integer dynamicThreshold;
}
