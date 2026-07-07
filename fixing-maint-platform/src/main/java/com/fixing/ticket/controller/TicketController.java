package com.fixing.ticket.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.Result;
import com.fixing.contract.vo.CoverageCard;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.domain.SoftwareInstance;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.equipment.mapper.SoftwareInstanceMapper;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketCharge;
import com.fixing.ticket.domain.TicketLog;
import com.fixing.ticket.dto.*;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.service.TicketService;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单接口（M4 核心）。Controller 只做三件事：收参数、@RequirePerm 挡入口、调 Service。
 *
 * <p>权限字符串（种子在 sql/seed.sql 的 sys_role_perm）：
 * maint:ticket:list 查看 · add 提交 · assign 派单/改派 · handle 接单/换件/完工 · confirm 确认/驳回/取消。
 * 改角色权限 = 改数据库，不改这里 —— 这就是权限字符串化的意义。
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final EquipmentMapper equipmentMapper;
    private final SoftwareInstanceMapper softwareInstanceMapper;

    public TicketController(TicketService ticketService, EquipmentMapper equipmentMapper,
                            SoftwareInstanceMapper softwareInstanceMapper) {
        this.ticketService = ticketService;
        this.equipmentMapper = equipmentMapper;
        this.softwareInstanceMapper = softwareInstanceMapper;
    }

    @RequirePerm("maint:ticket:add")
    @PostMapping
    public Result<Ticket> create(@Valid @RequestBody TicketCreateDTO dto) {
        return Result.ok(ticketService.create(dto));
    }

    @RequirePerm("maint:ticket:list")
    @GetMapping("/{id}")
    public Result<Ticket> get(@PathVariable Long id) {
        return Result.ok(ticketService.getById(id));
    }

    @RequirePerm("maint:ticket:list")
    @GetMapping("/{id}/logs")
    public Result<List<TicketLog>> logs(@PathVariable Long id) {
        return Result.ok(ticketService.getLogs(id));
    }

    /** 在保状态卡：派单/详情页顶部（保不保、哪份合同、免费件清单、计费提示） */
    @RequirePerm("maint:ticket:list")
    @GetMapping("/{id}/coverage")
    public Result<CoverageCard> coverage(@PathVariable Long id) {
        return Result.ok(ticketService.coverageCard(id));
    }

    /** 结算明细：不在保工单完工后自动生成（上门费/配件费/维修费） */
    @RequirePerm("maint:ticket:list")
    @GetMapping("/{id}/charges")
    public Result<List<TicketCharge>> charges(@PathVariable Long id) {
        return Result.ok(ticketService.getCharges(id));
    }

    @RequirePerm("maint:ticket:list")
    @GetMapping
    public Result<List<Ticket>> list(@RequestParam(required = false) TicketStatus status,
                                     @RequestParam(required = false) Priority priority,
                                     @RequestParam(required = false) Long engineerId,
                                     @RequestParam(required = false) Long customerId) {
        return Result.ok(ticketService.list(status, priority, engineerId, customerId));
    }

    /** 报修表单的设备下拉：客户只拿到自己单位的设备（数据隔离在查询里） */
    @RequirePerm("maint:ticket:add")
    @GetMapping("/my-equipments")
    public Result<List<Equipment>> myEquipments() {
        var me = UserContext.current();
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.CUSTOMER) {
            query.eq(Equipment::getCustomerId, me.getCustomerId());
        }
        return Result.ok(equipmentMapper.selectList(query));
    }

    /** 报修表单的软件下拉（软件维修/安装软件类）：同样按单位隔离 */
    @RequirePerm("maint:ticket:add")
    @GetMapping("/my-softwares")
    public Result<List<SoftwareInstance>> mySoftwares() {
        var me = UserContext.current();
        LambdaQueryWrapper<SoftwareInstance> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.CUSTOMER) {
            query.eq(SoftwareInstance::getCustomerId, me.getCustomerId());
        }
        return Result.ok(softwareInstanceMapper.selectList(query));
    }

    @RequirePerm("maint:ticket:assign")
    @PostMapping("/{id}/assign")
    public Result<Ticket> assign(@PathVariable Long id, @Valid @RequestBody TicketAssignDTO dto) {
        return Result.ok(ticketService.assign(id, dto));
    }

    @RequirePerm("maint:ticket:assign")
    @PostMapping("/{id}/reassign")
    public Result<Ticket> reassign(@PathVariable Long id, @Valid @RequestBody TicketAssignDTO dto) {
        return Result.ok(ticketService.reassign(id, dto));
    }

    @RequirePerm("maint:ticket:handle")
    @PostMapping("/{id}/accept")
    public Result<Ticket> accept(@PathVariable Long id, @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.accept(id, dto));
    }

    @RequirePerm("maint:ticket:handle")
    @PostMapping("/{id}/use-part")
    public Result<SparePartUsage> usePart(@PathVariable Long id, @Valid @RequestBody UsePartDTO dto) {
        return Result.ok(ticketService.usePart(id, dto));
    }

    @RequirePerm("maint:ticket:handle")
    @PostMapping("/{id}/complete")
    public Result<Ticket> complete(@PathVariable Long id, @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.complete(id, dto));
    }

    @RequirePerm("maint:ticket:confirm")
    @PostMapping("/{id}/confirm")
    public Result<Ticket> confirm(@PathVariable Long id, @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.confirm(id, dto));
    }

    @RequirePerm("maint:ticket:confirm")
    @PostMapping("/{id}/reject")
    public Result<Ticket> reject(@PathVariable Long id, @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.reject(id, dto));
    }

    /** 取消：客户(confirm 权限，仅待派单)或管理员(assign 权限)；细节规则在 Service */
    @RequirePerm({"maint:ticket:confirm", "maint:ticket:assign"})
    @PostMapping("/{id}/cancel")
    public Result<Ticket> cancel(@PathVariable Long id, @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.cancel(id, dto));
    }
}
