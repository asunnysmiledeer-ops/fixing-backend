package com.fixing.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务参数（平台端可改，替代写死在 yml 里的数值）：
 * 上门费/维修费标准、合同提醒天数…… 改完即刻生效，不重启不发版。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_param")
public class SysParam extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数键，如 charge.visit_fee */
    private String paramKey;

    /** 参数值（统一存字符串，取用时按需转型） */
    private String paramValue;

    /** 给人看的名字 */
    private String name;
}
