package com.fixing.contract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 新建合同请求体：主表字段 + 三组颗粒化绑定（设备/免费备件/软件）。 */
@Data
public class ContractSaveDTO {

    private Long customerId;
    private String name;
    private String scope;
    private LocalDate startDate;
    private LocalDate endDate;
    private String billingType;
    private BigDecimal amount;

    /** 这份合同保哪几台设备（在保判定的依据） */
    private List<Long> equipmentIds;

    /** 哪些备件在保内免费更换 */
    private List<Long> partIds;

    /** 保哪些软件实例 */
    private List<Long> softwareInstanceIds;
}
