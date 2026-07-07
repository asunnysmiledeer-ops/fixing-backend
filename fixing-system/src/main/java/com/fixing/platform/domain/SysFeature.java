package com.fixing.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 功能开关（feature flag）——"添加功能直接在平台端更改"的落点。
 * 新功能一律挂开关：平台端一键启停，全平台入口随之出现/消失，不发版。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_feature")
public class SysFeature extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 功能键，如 resident_engineer */
    private String featureKey;

    /** 给人看的名字，如 "驻场工程师模式" */
    private String name;

    /** 开/关 */
    private Boolean enabled;

    private String remark;
}
