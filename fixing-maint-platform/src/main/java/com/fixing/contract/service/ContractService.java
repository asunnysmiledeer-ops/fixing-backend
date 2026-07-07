package com.fixing.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.common.BusinessException;
import com.fixing.contract.domain.*;
import com.fixing.contract.dto.ContractSaveDTO;
import com.fixing.contract.dto.ServiceNotice;
import com.fixing.contract.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 合同服务。M1 两个变化：
 * 1. 保存合同 = 主表 + 三张绑定明细（设备/免费备件/软件）一个事务；
 * 2. 服务到期的语义变了：到期**不再禁止报修**，而是提示"将按次收费"——
 *    不在保是收入来源，不是拒客理由（用户定案）。
 */
@Service
public class ContractService {

    /** 到期前几天开始提醒（默认值；实际值走平台参数 contract.remind_days，可在平台端改） */
    public static final int REMIND_DAYS = 7;

    private final com.fixing.platform.service.ParamService paramService;
    private final ContractMapper contractMapper;
    private final ContractEquipmentMapper contractEquipmentMapper;
    private final ContractPartMapper contractPartMapper;
    private final ContractSoftwareMapper contractSoftwareMapper;

    public ContractService(com.fixing.platform.service.ParamService paramService,
                           ContractMapper contractMapper,
                           ContractEquipmentMapper contractEquipmentMapper,
                           ContractPartMapper contractPartMapper,
                           ContractSoftwareMapper contractSoftwareMapper) {
        this.paramService = paramService;
        this.contractMapper = contractMapper;
        this.contractEquipmentMapper = contractEquipmentMapper;
        this.contractPartMapper = contractPartMapper;
        this.contractSoftwareMapper = contractSoftwareMapper;
    }

    /** 合同列表（customerId 空 = 全部） */
    public List<Contract> list(Long customerId) {
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(customerId != null, Contract::getCustomerId, customerId)
                .orderByDesc(Contract::getEndDate));
    }

    /** 新建合同 + 三张绑定明细，同一事务（明细失败整单回滚，不留半截合同） */
    @Transactional
    public Contract save(ContractSaveDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException("合同结束日期不能早于开始日期");
        }
        Contract contract = new Contract();
        contract.setCustomerId(dto.getCustomerId());
        contract.setName(dto.getName());
        contract.setScope(dto.getScope());
        contract.setStartDate(dto.getStartDate());
        contract.setEndDate(dto.getEndDate());
        contract.setBillingType(dto.getBillingType() == null ? "YEARLY" : dto.getBillingType());
        contract.setAmount(dto.getAmount());
        contract.setStatus("ACTIVE");
        contractMapper.insert(contract);

        if (dto.getEquipmentIds() != null) {
            for (Long eid : dto.getEquipmentIds()) {
                ContractEquipment link = new ContractEquipment();
                link.setContractId(contract.getId());
                link.setEquipmentId(eid);
                contractEquipmentMapper.insert(link);
            }
        }
        if (dto.getPartIds() != null) {
            for (Long pid : dto.getPartIds()) {
                ContractPart link = new ContractPart();
                link.setContractId(contract.getId());
                link.setPartId(pid);
                contractPartMapper.insert(link);
            }
        }
        if (dto.getSoftwareInstanceIds() != null) {
            for (Long sid : dto.getSoftwareInstanceIds()) {
                ContractSoftware link = new ContractSoftware();
                link.setContractId(contract.getId());
                link.setSoftwareInstanceId(sid);
                contractSoftwareMapper.insert(link);
            }
        }
        return contract;
    }

    /** 合同的三组绑定 id（详情/编辑回显用） */
    public ContractSaveDTO bindings(Long contractId) {
        ContractSaveDTO dto = new ContractSaveDTO();
        dto.setEquipmentIds(contractEquipmentMapper.selectList(new LambdaQueryWrapper<ContractEquipment>()
                .eq(ContractEquipment::getContractId, contractId))
                .stream().map(ContractEquipment::getEquipmentId).toList());
        dto.setPartIds(contractPartMapper.selectList(new LambdaQueryWrapper<ContractPart>()
                .eq(ContractPart::getContractId, contractId))
                .stream().map(ContractPart::getPartId).toList());
        dto.setSoftwareInstanceIds(contractSoftwareMapper.selectList(new LambdaQueryWrapper<ContractSoftware>()
                .eq(ContractSoftware::getContractId, contractId))
                .stream().map(ContractSoftware::getSoftwareInstanceId).toList());
        return dto;
    }

    /** 提醒窗口天数（平台参数 contract.remind_days 可配，REMIND_DAYS 兜底） */
    public int remindDays() {
        return paramService.getInt("contract.remind_days", REMIND_DAYS);
    }

    public void terminate(Long id) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在: id=" + id);
        }
        contract.setStatus("TERMINATED");
        contractMapper.updateById(contract);
    }

    /**
     * 客户服务状态提示（登录后前端展示）：
     * EXPIRED  = 没有任何生效合同 → 提示"报修将按次收费"（不再拦截报修）
     * EXPIRING = 最晚到期日在 7 天内 → 倒计时提醒
     */
    public ServiceNotice noticeFor(Long customerId) {
        LocalDate today = LocalDate.now();
        List<Contract> valid = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getCustomerId, customerId)
                .eq(Contract::getStatus, "ACTIVE")
                .ge(Contract::getEndDate, today));
        if (valid.isEmpty()) {
            return new ServiceNotice("EXPIRED",
                    "您当前没有生效的维保合同，报修将按次收费（上门费+配件费+维修费），如需包年服务请联系平台续约", null);
        }
        LocalDate latestEnd = valid.stream().map(Contract::getEndDate).max(LocalDate::compareTo).orElseThrow();
        long daysLeft = ChronoUnit.DAYS.between(today, latestEnd);
        if (daysLeft <= remindDays()) {
            return new ServiceNotice("EXPIRING",
                    "您的维保合同将于 " + latestEnd + " 到期（剩 " + daysLeft + " 天），到期后报修将按次收费，请及时续约",
                    (int) daysLeft);
        }
        return new ServiceNotice("OK", null, (int) daysLeft);
    }
}
