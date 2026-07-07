package com.fixing.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 合同↔备件明细：哪些备件在保内免费更换。
 * 换件时：在保工单 + 备件在此清单 → 免费；否则计入配件费（按次收费）。
 */
@Data
@TableName("contract_part")
public class ContractPart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long partId;
}
