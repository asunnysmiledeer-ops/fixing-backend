package com.fixing.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.Result;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import com.fixing.inventory.service.InventoryService;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存接口。列表带动态阈值（随在保签约设备数自动调整）；
 * 领料流水：工程师强制只看自己的（数据隔离），管理员全量。
 */
@RestController
public class InventoryController {

    private final InventoryService inventoryService;
    private final SparePartMapper sparePartMapper;
    private final SparePartUsageMapper usageMapper;

    public InventoryController(InventoryService inventoryService,
                               SparePartMapper sparePartMapper, SparePartUsageMapper usageMapper) {
        this.inventoryService = inventoryService;
        this.sparePartMapper = sparePartMapper;
        this.usageMapper = usageMapper;
    }

    @RequirePerm("maint:part:edit")
    @PostMapping("/spare-parts")
    public Result<SparePart> create(@Valid @RequestBody SparePart part) {
        sparePartMapper.insert(part);
        return Result.ok(part);
    }

    @RequirePerm("maint:part:list")
    @GetMapping("/spare-parts")
    public Result<List<SparePart>> list() {
        return Result.ok(inventoryService.listWithDynamicThreshold());
    }

    /** 低库存预警清单（库存 < 动态阈值） */
    @RequirePerm("maint:part:list")
    @GetMapping("/spare-parts/low-stock")
    public Result<List<SparePart>> lowStock() {
        return Result.ok(inventoryService.lowStock());
    }

    @RequirePerm("maint:part:list")
    @GetMapping("/part-usages")
    public Result<List<SparePartUsage>> usages(@RequestParam(required = false) Long ticketId,
                                               @RequestParam(required = false) Long engineerId) {
        SysUser me = UserContext.current();
        LambdaQueryWrapper<SparePartUsage> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.ENGINEER) {
            query.eq(SparePartUsage::getEngineerId, me.getId()); // 传参不认，只看自己
        } else if (engineerId != null) {
            query.eq(SparePartUsage::getEngineerId, engineerId);
        }
        query.eq(ticketId != null, SparePartUsage::getTicketId, ticketId)
             .orderByDesc(SparePartUsage::getCreateTime);
        return Result.ok(usageMapper.selectList(query));
    }
}
