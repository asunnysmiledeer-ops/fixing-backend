package com.fixing.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.mapper.TicketMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备台账接口（M2 的设备部分）。
 * 除 CRUD 外，还提供"按设备查历史工单"—— D 阶段的验收点。
 */
@RestController
@RequestMapping("/equipments")
public class EquipmentController {

    private final EquipmentMapper equipmentMapper;
    private final TicketMapper ticketMapper;

    public EquipmentController(EquipmentMapper equipmentMapper, TicketMapper ticketMapper) {
        this.equipmentMapper = equipmentMapper;
        this.ticketMapper = ticketMapper;
    }

    /** 新建设备 */
    @PostMapping
    public Result<Equipment> create(@Valid @RequestBody Equipment equipment) {
        equipmentMapper.insert(equipment);
        return Result.ok(equipment);
    }

    /** 设备列表，可按客户过滤：GET /equipments?customerId=1 */
    @GetMapping
    public Result<List<Equipment>> list(@RequestParam(required = false) Long customerId) {
        // LambdaQueryWrapper：用方法引用代替手写列名字符串，改字段名时编译器帮你查错
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        if (customerId != null) {
            query.eq(Equipment::getCustomerId, customerId);
        }
        return Result.ok(equipmentMapper.selectList(query));
    }

    /** 设备详情 */
    @GetMapping("/{id}")
    public Result<Equipment> get(@PathVariable Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException("设备不存在: id=" + id);
        }
        return Result.ok(equipment);
    }

    /**
     * 按设备查它的历史工单（设备维修档案）。
     * ✅ 验收点：D 阶段"能按设备查它的历史工单"。
     */
    @GetMapping("/{id}/tickets")
    public Result<List<Ticket>> ticketHistory(@PathVariable Long id) {
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEquipmentId, id)
                .orderByDesc(Ticket::getCreatedAt);
        return Result.ok(ticketMapper.selectList(query));
    }
}
