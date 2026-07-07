package com.fixing.equipment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备实体。equipmentType 同时是备件"动态阈值"的匹配键。
 * 设备是否"在保"由 contract_equipment 明细决定（精确到台），不再看客户整体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment")
public class Equipment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    private String equipmentType;
    private String model;

    /** 序列号，唯一（扫码报修定位键） */
    private String serialNo;

    private String location;

    /** NORMAL/FAULT/SCRAPPED */
    private String status;

    /** 运送/交付日期（查询系统的筛选维度之一） */
    private LocalDate deliveredAt;

    /** 维修次数（查询时按工单统计的计算列，表里没有对应字段） */
    @TableField(exist = false)
    private Long repairCount;
}
