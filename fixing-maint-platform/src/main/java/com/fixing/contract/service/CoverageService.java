package com.fixing.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.domain.ContractEquipment;
import com.fixing.contract.domain.ContractPart;
import com.fixing.contract.mapper.ContractEquipmentMapper;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.mapper.ContractPartMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 在保判定服务 —— 合同颗粒化绑定的"读取侧"，三处业务共用：
 * ① 报修时固化 covered/contractId 快照；② 换件时判定该备件免不免费；③ 派单界面的在保状态卡。
 *
 * <p>判定口径（与动态库存阈值的 SQL 同一口径）：
 * 设备"在保" = 出现在某份 status=ACTIVE 且 end_date>=今天 的合同的设备明细里。
 */
@Service
public class CoverageService {

    private final ContractMapper contractMapper;
    private final ContractEquipmentMapper contractEquipmentMapper;
    private final ContractPartMapper contractPartMapper;

    public CoverageService(ContractMapper contractMapper,
                           ContractEquipmentMapper contractEquipmentMapper,
                           ContractPartMapper contractPartMapper) {
        this.contractMapper = contractMapper;
        this.contractEquipmentMapper = contractEquipmentMapper;
        this.contractPartMapper = contractPartMapper;
    }

    /**
     * 找出覆盖该设备的生效合同；无 → null（= 不在保，按次收费）。
     * 多份合同同时覆盖时取到期日最晚的一份（对客户最有利）。
     */
    public Contract findCoveringContract(Long equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        List<ContractEquipment> links = contractEquipmentMapper.selectList(
                new LambdaQueryWrapper<ContractEquipment>()
                        .eq(ContractEquipment::getEquipmentId, equipmentId));
        if (links.isEmpty()) {
            return null;
        }
        List<Long> contractIds = links.stream().map(ContractEquipment::getContractId).toList();
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                        .in(Contract::getId, contractIds)
                        .eq(Contract::getStatus, "ACTIVE")
                        .ge(Contract::getEndDate, LocalDate.now())
                        .orderByDesc(Contract::getEndDate))
                .stream().findFirst().orElse(null);
    }

    /** 该备件是否在合同的"免费更换清单"里（颗粒化：精确到件） */
    public boolean isPartFree(Long contractId, Long partId) {
        if (contractId == null) {
            return false; // 不在保 → 一切备件计费
        }
        return contractPartMapper.selectCount(new LambdaQueryWrapper<ContractPart>()
                .eq(ContractPart::getContractId, contractId)
                .eq(ContractPart::getPartId, partId)) > 0;
    }
}
