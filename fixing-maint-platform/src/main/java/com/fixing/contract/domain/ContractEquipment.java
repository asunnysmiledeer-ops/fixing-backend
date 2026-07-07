package com.fixing.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 合同↔设备明细：这份合同精确保哪几台机器（"在保"判定的依据）。 */
@Data
@TableName("contract_equipment")
public class ContractEquipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long equipmentId;
}
