package com.fixing.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售订单（M1.5）：超管录入"客户要新机器/新软件"，管理员执行派发。
 * 状态：PENDING 待派发 → DISPATCHED 已派发（派发 = 扣整机库存 + 生成设备/软件档案 + 自动建安装工单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_order")
public class SalesOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 SO+日期+序号 */
    private String orderNo;

    private Long customerId;

    /** MACHINE 整机 / SOFTWARE 软件 */
    private String orderType;

    /** 整机订单：机型（对应 machine_stock.model） */
    private String model;

    /** 软件订单：软件名与版本 */
    private String softwareName;
    private String softwareVersion;

    /** 数量（软件订单恒为 1 套） */
    private Integer qty;

    private String remark;

    /** PENDING / DISPATCHED */
    private String status;
}
