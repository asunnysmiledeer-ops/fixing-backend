package com.fixing.ticket.controller;

import com.fixing.common.Result;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketLog;
import com.fixing.ticket.dto.*;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单接口（M4）。Controller 只做三件事：收参数、调 Service、包 Result ——
 * 不写任何业务逻辑（业务逻辑堆 Controller 是三层架构的头号反模式）。
 *
 * <p>状态变更接口全部做成"动作"风格：POST /tickets/{id}/assign、/accept…
 * 而不是 PUT /tickets/{id} 直接改 status 字段 —— 动作有明确的语义和权限，
 * 直接改字段则等于绕过状态机。
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** UC1 提交报修 */
    @PostMapping
    public Result<Ticket> create(@Valid @RequestBody TicketCreateDTO dto) {
        return Result.ok(ticketService.create(dto));
    }

    /** UC2 工单详情 */
    @GetMapping("/{id}")
    public Result<Ticket> get(@PathVariable Long id) {
        return Result.ok(ticketService.getById(id));
    }

    /** UC2 流转记录（进度时间线） */
    @GetMapping("/{id}/logs")
    public Result<List<TicketLog>> logs(@PathVariable Long id) {
        return Result.ok(ticketService.getLogs(id));
    }

    /** 列表筛选：GET /tickets?status=&priority=&engineerId=&customerId= （全部可选） */
    @GetMapping
    public Result<List<Ticket>> list(@RequestParam(required = false) TicketStatus status,
                                     @RequestParam(required = false) Priority priority,
                                     @RequestParam(required = false) Long engineerId,
                                     @RequestParam(required = false) Long customerId) {
        return Result.ok(ticketService.list(status, priority, engineerId, customerId));
    }

    /** UC5 派单（管理员） */
    @PostMapping("/{id}/assign")
    public Result<Ticket> assign(@PathVariable Long id, @Valid @RequestBody TicketAssignDTO dto) {
        return Result.ok(ticketService.assign(id, dto));
    }

    /** UC6 改派/重派（管理员，SLA 超时也走这里） */
    @PostMapping("/{id}/reassign")
    public Result<Ticket> reassign(@PathVariable Long id, @Valid @RequestBody TicketAssignDTO dto) {
        return Result.ok(ticketService.reassign(id, dto));
    }

    /** UC7 接单（被派的工程师本人） */
    @PostMapping("/{id}/accept")
    public Result<Ticket> accept(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.accept(id, dto));
    }

    /** UC8 完工提交（责任工程师） */
    @PostMapping("/{id}/complete")
    public Result<Ticket> complete(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.complete(id, dto));
    }

    /** UC3 客户确认完工 */
    @PostMapping("/{id}/confirm")
    public Result<Ticket> confirm(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.confirm(id, dto));
    }

    /** UC3 客户驳回（没修好，打回处理中） */
    @PostMapping("/{id}/reject")
    public Result<Ticket> reject(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.reject(id, dto));
    }

    /** UC4 取消（客户仅待派单时；管理员待派单/已派单） */
    @PostMapping("/{id}/cancel")
    public Result<Ticket> cancel(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        return Result.ok(ticketService.cancel(id, dto));
    }

    /** E 阶段核心演示点：维修中换件 → 扣库存 + 领料流水（责任工程师，工单须"处理中"） */
    @PostMapping("/{id}/use-part")
    public Result<SparePartUsage> usePart(@PathVariable Long id, @Valid @RequestBody UsePartDTO dto) {
        return Result.ok(ticketService.usePart(id, dto));
    }
}
