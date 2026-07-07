package com.fixing.inventory.controller;

import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.inventory.domain.MachineStock;
import com.fixing.inventory.mapper.MachineStockMapper;
import com.fixing.inventory.service.AssemblyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 整机库存接口（管理员）：机型台账 + 组装（配件→整机）。
 */
@RestController
@RequestMapping("/machines")
public class MachineController {

    private final MachineStockMapper machineStockMapper;
    private final AssemblyService assemblyService;

    public MachineController(MachineStockMapper machineStockMapper, AssemblyService assemblyService) {
        this.machineStockMapper = machineStockMapper;
        this.assemblyService = assemblyService;
    }

    @RequirePerm("maint:machine:list")
    @GetMapping
    public Result<List<MachineStock>> list() {
        return Result.ok(machineStockMapper.selectList(null));
    }

    /** 新建机型（初始库存可为 0，靠组装入库） */
    @RequirePerm("maint:machine:edit")
    @PostMapping
    public Result<MachineStock> create(@RequestBody MachineStock stock) {
        if (stock.getQty() == null) {
            stock.setQty(0);
        }
        machineStockMapper.insert(stock);
        return Result.ok(stock);
    }

    /** 组装：body = {machineStockId, qty, parts: [{partId, qty}], remark} */
    @RequirePerm("maint:machine:edit")
    @PostMapping("/assemble")
    @SuppressWarnings("unchecked")
    public Result<Object> assemble(@RequestBody Map<String, Object> body) {
        return Result.ok(assemblyService.assemble(
                Long.valueOf(body.get("machineStockId").toString()),
                Integer.parseInt(body.get("qty").toString()),
                (List<Map<String, Object>>) body.get("parts"),
                body.get("remark") == null ? null : body.get("remark").toString()));
    }
}
