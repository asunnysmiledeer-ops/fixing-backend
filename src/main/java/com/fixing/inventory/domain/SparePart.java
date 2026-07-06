package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件实体，对应表 spare_part。
 * 分类三种：配件 / 部件 / 耗材（v0 用字符串，v1 进配置字典）。
 */
@Data
@TableName("spare_part")
public class SparePart {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 备件名称，如 "80mm 热敏打印头" */
    private String name;

    /** 分类：PART 配件 / COMPONENT 部件 / CONSUMABLE 耗材 */
    private String category;

    /** 当前库存量。扣减必须走原子 SQL（见 SparePartMapper.deductStock），不能读出来减完再写回 */
    private Integer stockQty;

    /** 低库存阈值：stockQty < 该值 → 触发预警（每种备件按消耗速度单独设） */
    private Integer lowStockThreshold;

    /** 单价。金额一律用 BigDecimal —— double 有精度误差，算钱会出事 */
    private BigDecimal unitPrice;

    private LocalDateTime createdAt;
}
