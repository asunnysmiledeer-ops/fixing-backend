package com.fixing.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
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

    /** 新建设备 —— 台账维护是管理端的事 */
    @PostMapping
    public Result<Equipment> create(@Valid @RequestBody Equipment equipment) {
        UserContext.require(UserRole.ADMIN);
        equipmentMapper.insert(equipment);
        return Result.ok(equipment);
    }

    /**
     * 设备列表，可按客户过滤：GET /equipments?customerId=1
     * 【数据隔离】客户角色强制只返回自己单位的设备（报修选设备的下拉框数据源）。
     */
    @GetMapping
    public Result<List<Equipment>> list(@RequestParam(required = false) Long customerId) {
        SysUser me = UserContext.current();
        // LambdaQueryWrapper：用方法引用代替手写列名字符串，改字段名时编译器帮你查错
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.CUSTOMER) {
            query.eq(Equipment::getCustomerId, me.getCustomerId()); // 客户端传的 customerId 不认
        } else if (customerId != null) {
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
        SysUser me = UserContext.current();
        // 客户只能查自己单位设备的历史（工程师查维修档案、管理员全量，放行）
        if (me.getRole() == UserRole.CUSTOMER) {
            Equipment equipment = equipmentMapper.selectById(id);
            if (equipment == null || !equipment.getCustomerId().equals(me.getCustomerId())) {
                throw new BusinessException("无权查看: 该设备不属于你的单位");
            }
        }
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEquipmentId, id)
                .orderByDesc(Ticket::getCreatedAt);
        return Result.ok(ticketMapper.selectList(query));
    }
}
