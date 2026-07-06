package com.fixing.customer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户实体，对应表 customer。
 *
 * <p>【核心设计心法】平台不写死行业 —— "医院"只是客户的一种，
 * 用 customerType 字段区分（HOSPITAL/SCHOOL/FACTORY…），换行业不改骨架。
 * v1 会把 customerType 做成可配置字典（M14），v0 先当普通字符串。
 */
@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户名称，如 "市第一人民医院" */
    private String name;

    /** 客户类型：不写死 hospital，行业差异交给配置 */
    private String customerType;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 地址（工程师上门要用） */
    private String address;

    private LocalDateTime createdAt;
}
