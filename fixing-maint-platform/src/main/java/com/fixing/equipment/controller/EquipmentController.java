package com.fixing.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备台账接口。"添加机器"类工单完工后，管理员在这里登记新设备
 * → 一并进入合同绑定与库存动态阈值的计算基数。
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

    @RequirePerm("maint:equipment:edit")
    @PostMapping
    public Result<Equipment> create(@Valid @RequestBody Equipment equipment) {
        if (equipment.getStatus() == null) {
            equipment.setStatus("NORMAL");
        }
        equipmentMapper.insert(equipment);
        return Result.ok(equipment);
    }

    /** 列表：客户角色强制只回自己单位的设备（数据隔离） */
    @RequirePerm("maint:equipment:list")
    @GetMapping
    public Result<List<Equipment>> list(@RequestParam(required = false) Long customerId) {
        SysUser me = UserContext.current();
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.CUSTOMER) {
            query.eq(Equipment::getCustomerId, me.getCustomerId());
        } else if (customerId != null) {
            query.eq(Equipment::getCustomerId, customerId);
        }
        return Result.ok(equipmentMapper.selectList(query));
    }

    /** 设备维修历史（客户只能查自己单位设备的） */
    @RequirePerm("maint:equipment:list")
    @GetMapping("/{id}/tickets")
    public Result<List<Ticket>> ticketHistory(@PathVariable Long id) {
        SysUser me = UserContext.current();
        if (me.getRole() == UserRole.CUSTOMER) {
            Equipment equipment = equipmentMapper.selectById(id);
            if (equipment == null || !equipment.getCustomerId().equals(me.getCustomerId())) {
                throw new BusinessException("无权查看: 该设备不属于你的单位");
            }
        }
        return Result.ok(ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEquipmentId, id).orderByDesc(Ticket::getCreateTime)));
    }
}
