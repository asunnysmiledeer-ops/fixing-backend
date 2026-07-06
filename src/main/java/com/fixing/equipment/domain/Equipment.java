package com.fixing.equipment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体，对应表 equipment。
 *
 * <p>同 Customer 的心法："叫号机"只是设备的一种，用 equipmentType 区分，
 * 平台骨架不绑定任何具体设备。设备属于某个客户（customer_id 外键）。
 */
@Data
@TableName("equipment")
public class Equipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属客户 id（外键 → customer.id） */
    private Long customerId;

    /** 设备类型：如 叫号机/自助机/打印机，v1 变成可配置字典 */
    private String equipmentType;

    /** 型号 */
    private String model;

    /** 序列号，唯一（设备的"身份证"，将来二维码扫码报修就靠它定位设备） */
    private String serialNo;

    /** 安装位置，如 "门诊大厅一楼" */
    private String location;

    /** 设备状态：NORMAL 正常 / FAULT 故障 / SCRAPPED 报废（v0 用字符串，不单独建枚举） */
    private String status;

    private LocalDateTime createdAt;
}
