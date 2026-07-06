package com.fixing.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.UserContext;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
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

    /** 新增备件（入库 v0 简化为直接建带库存的备件）—— 管理端专属 */
    @PostMapping("/spare-parts")
    public Result<SparePart> create(@Valid @RequestBody SparePart part) {
        UserContext.require(UserRole.ADMIN);
        sparePartMapper.insert(part);
        return Result.ok(part);
    }

    /** 备件列表（工程师看余量、管理员管库存；客户角色不给看） */
    @GetMapping("/spare-parts")
    public Result<List<SparePart>> list() {
        UserContext.require(UserRole.ADMIN, UserRole.ENGINEER);
        return Result.ok(sparePartMapper.selectList(null));
    }

    /**
     * 低库存预警清单：stock_qty < low_stock_threshold 的备件。
     * 工程师端登录就展示（缺什么件出门前心里有数），管理端 Dashboard 也用。
     */
    @GetMapping("/spare-parts/low-stock")
    public Result<List<SparePart>> lowStock() {
        UserContext.require(UserRole.ADMIN, UserRole.ENGINEER);
        // apply 拼原生 SQL 片段：两列比较 Wrapper 没有现成方法。没有用户输入，无注入风险
        return Result.ok(sparePartMapper.selectList(new LambdaQueryWrapper<SparePart>()
                .apply("stock_qty < low_stock_threshold")));
    }

    /**
     * 领料流水查询，可按工单或工程师过滤。
     * 【数据隔离】工程师只能看自己的领料记录；管理员全量；客户无此功能。
     */
    @GetMapping("/part-usages")
    public Result<List<SparePartUsage>> usages(@RequestParam(required = false) Long ticketId,
                                               @RequestParam(required = false) Long engineerId) {
        SysUser me = UserContext.require(UserRole.ADMIN, UserRole.ENGINEER);
        LambdaQueryWrapper<SparePartUsage> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.ENGINEER) {
            query.eq(SparePartUsage::getEngineerId, me.getId()); // 强制只看自己，传参不认
        } else if (engineerId != null) {
            query.eq(SparePartUsage::getEngineerId, engineerId);
        }
        if (ticketId != null) {
            query.eq(SparePartUsage::getTicketId, ticketId);
        }
        query.orderByDesc(SparePartUsage::getCreatedAt);
        return Result.ok(usageMapper.selectList(query));
    }
}
