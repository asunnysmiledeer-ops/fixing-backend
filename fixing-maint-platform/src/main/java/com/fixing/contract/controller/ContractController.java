package com.fixing.contract.controller;

import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.Result;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.dto.ContractSaveDTO;
import com.fixing.contract.dto.ServiceNotice;
import com.fixing.contract.service.ContractService;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同接口。M1：新建合同带三组颗粒化绑定（设备/免费备件/软件）。
 * /service-notice 是唯一对客户开放的端点（登录横幅数据源），其余管理端专属。
 */
@RestController
@RequestMapping("/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @RequirePerm("maint:contract:list")
    @GetMapping
    public Result<List<Contract>> list(@RequestParam(required = false) Long customerId) {
        return Result.ok(contractService.list(customerId));
    }

    /** 合同的绑定明细（详情展示/编辑回显） */
    @RequirePerm("maint:contract:list")
    @GetMapping("/{id}/bindings")
    public Result<ContractSaveDTO> bindings(@PathVariable Long id) {
        return Result.ok(contractService.bindings(id));
    }

    /** 新建合同 + 绑定，一个事务 */
    @RequirePerm("maint:contract:edit")
    @PostMapping
    public Result<Contract> create(@Valid @RequestBody ContractSaveDTO dto) {
        return Result.ok(contractService.save(dto));
    }

    @RequirePerm("maint:contract:edit")
    @PostMapping("/{id}/terminate")
    public Result<Void> terminate(@PathVariable Long id) {
        contractService.terminate(id);
        return Result.ok();
    }

    /**
     * 客户服务状态（登录后前端横幅）：无 @RequirePerm —— 任何登录者可查，
     * 非客户角色恒返回 OK。到期语义 = 提示按次收费，不再拦截报修。
     */
    @GetMapping("/service-notice")
    public Result<ServiceNotice> serviceNotice() {
        SysUser me = UserContext.current();
        if (me.getRole() == UserRole.CUSTOMER && me.getCustomerId() != null) {
            return Result.ok(contractService.noticeFor(me.getCustomerId()));
        }
        return Result.ok(new ServiceNotice("OK", null, null));
    }
}
