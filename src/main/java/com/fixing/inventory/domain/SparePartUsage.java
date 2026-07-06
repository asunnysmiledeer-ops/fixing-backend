package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领料记录实体，对应表 spare_part_usage。
 *
 * <p>【设计心法：想统计先记细】每次用件都记全 "谁 / 哪张工单 / 哪台设备 / 几个"，
 * 将来的工程师用量统计、维修成本核算、库存流水，全都从这张表顺手聚合出来。
 * 只改 stock_qty 不记流水的话，这些统计就永远做不了了。
 */
@Data
@TableName("spare_part_usage")
public class SparePartUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用的哪个备件（外键 → spare_part.id） */
    private Long partId;

    /** 用了几个 */
    private Integer qty;

    /** 谁用的（外键 → sys_user.id，工程师） */
    private Long engineerId;

    /** 用在哪张工单上（外键 → ticket.id） */
    private Long ticketId;

    /** 修的哪台设备（外键 → equipment.id，冗余存一份方便按设备统计） */
    private Long equipmentId;

    private LocalDateTime createdAt;
}
