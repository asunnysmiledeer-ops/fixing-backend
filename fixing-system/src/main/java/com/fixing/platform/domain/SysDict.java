package com.fixing.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典 —— "行业差异交给配置"的核心（M14 第一块实地）。
 * customer_type / equipment_type / part_category 等可选值都从这里来，
 * 平台端增删一条字典 = 全平台下拉框多/少一个选项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型：customer_type / equipment_type / part_category */
    private String dictType;

    /** 存库的值，如 HOSPITAL */
    private String dictValue;

    /** 给人看的标签，如 医院 */
    private String dictLabel;

    /** 排序 */
    private Integer sort;
}
