package com.fixing.equipment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 软件实例（M1 新增）：某客户装的某套软件与当前版本。
 * 软件维修/安装软件类工单挂到具体实例，合同通过 contract_software 绑定保哪些软件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("software_instance")
public class SoftwareInstance extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属客户 */
    private Long customerId;

    /** 装在哪台设备上（纯远程软件可空） */
    private Long equipmentId;

    /** 软件名，如 "叫号系统" */
    private String name;

    /** 当前版本，如 "v3.2.1"（升级完工后由管理端维护） */
    private String version;
}
