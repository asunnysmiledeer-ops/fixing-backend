package com.fixing.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 合同↔软件明细：保哪些软件实例的修复与升级。 */
@Data
@TableName("contract_software")
public class ContractSoftware {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long softwareInstanceId;
}
