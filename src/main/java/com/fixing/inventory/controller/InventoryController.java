package com.fixing.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.common.Result;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存接口（M8 最小集）：备件的增/查 + 领料流水查询。
 * 出入库单据、低库存预警等留给 v1。
 */
@RestController
public class InventoryController {

    private final SparePartMapper sparePartMapper;
    private final SparePartUsageMapper usageMapper;

    public InventoryController(SparePartMapper sparePartMapper, SparePartUsageMapper usageMapper) {
        this.sparePartMapper = sparePartMapper;
        this.usageMapper = usageMapper;
    }

    /** 新增备件（入库 v0 简化为直接建带库存的备件） */
    @PostMapping("/spare-parts")
    public Result<SparePart> create(@Valid @RequestBody SparePart part) {
        sparePartMapper.insert(part);
        return Result.ok(part);
    }

    /** 备件列表（看库存余量） */
    @GetMapping("/spare-parts")
    public Result<List<SparePart>> list() {
        return Result.ok(sparePartMapper.selectList(null));
    }

    /**
     * 领料流水查询，可按工单或工程师过滤：
     * GET /part-usages?ticketId=1 → 这张工单用了哪些件（✅ E 阶段验收点）
     * GET /part-usages?engineerId=3 → 该工程师的用量统计原始数据
     */
    @GetMapping("/part-usages")
    public Result<List<SparePartUsage>> usages(@RequestParam(required = false) Long ticketId,
                                               @RequestParam(required = false) Long engineerId) {
        LambdaQueryWrapper<SparePartUsage> query = new LambdaQueryWrapper<>();
        if (ticketId != null) {
            query.eq(SparePartUsage::getTicketId, ticketId);
        }
        if (engineerId != null) {
            query.eq(SparePartUsage::getEngineerId, engineerId);
        }
        query.orderByDesc(SparePartUsage::getCreatedAt);
        return Result.ok(usageMapper.selectList(query));
    }
}
