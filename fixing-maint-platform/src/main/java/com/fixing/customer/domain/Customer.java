package com.fixing.customer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户实体。医院只是 customerType 的一种取值 —— 平台不绑定行业。
 * 审计/租户/软删字段由 BaseEntity 提供（真实项目三件套）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer")
public class Customer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户名称 */
    private String name;

    /** 客户类型（HOSPITAL/CLINIC…，v0.5 进字典配置） */
    private String customerType;

    private String contactName;
    private String contactPhone;
    private String address;
}
