package com.fixing.contract.controller;

import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.service.ContractService;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同接口（M3 最小版）—— 管理端专属。
 * 每个方法第一行 UserContext.require(ADMIN)：非管理员直接拒之门外，
 * 前端"不展示入口"只是体验，这一行才是安全。
 */
@RestController
@RequestMapping("/contracts")
public class ContractController {

    private final ContractMapper contractMapper;
    private final ContractService contractService;

    public ContractController(ContractMapper contractMapper, ContractService contractService) {
        this.contractMapper = contractMapper;
        this.contractService = contractService;
    }

    /** 合同列表，可按客户过滤 */
    @GetMapping
    public Result<List<Contract>> list(@RequestParam(required = false) Long customerId) {
        UserContext.require(UserRole.ADMIN);
        return Result.ok(contractService.listByCustomer(customerId));
    }

    /** 新建合同 */
    @PostMapping
    public Result<Contract> create(@Valid @RequestBody Contract contract) {
        UserContext.require(UserRole.ADMIN);
        if (contract.getEndDate().isBefore(contract.getStartDate())) {
            throw new BusinessException("合同结束日期不能早于开始日期");
        }
        contract.setStatus("ACTIVE");
        contractMapper.insert(contract);
        return Result.ok(contract);
    }

    /** 终止合同（到期不续/提前解约） */
    @PostMapping("/{id}/terminate")
    public Result<Contract> terminate(@PathVariable Long id) {
        UserContext.require(UserRole.ADMIN);
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在: id=" + id);
        }
        contract.setStatus("TERMINATED");
        contractMapper.updateById(contract);
        return Result.ok(contract);
    }
}
