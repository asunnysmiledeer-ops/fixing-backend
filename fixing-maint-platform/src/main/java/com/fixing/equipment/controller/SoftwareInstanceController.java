package com.fixing.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.equipment.domain.SoftwareInstance;
import com.fixing.equipment.mapper.SoftwareInstanceMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 软件实例台账（管理端）：某客户装了什么软件、什么版本。
 * "安装软件"工单完工后在这里登记/升版本；合同通过 contract_software 绑定保哪些。
 */
@RestController
@RequestMapping("/softwares")
public class SoftwareInstanceController {

    private final SoftwareInstanceMapper softwareInstanceMapper;

    public SoftwareInstanceController(SoftwareInstanceMapper softwareInstanceMapper) {
        this.softwareInstanceMapper = softwareInstanceMapper;
    }

    @RequirePerm("maint:equipment:list")
    @GetMapping
    public Result<List<SoftwareInstance>> list(@RequestParam(required = false) Long customerId) {
        return Result.ok(softwareInstanceMapper.selectList(new LambdaQueryWrapper<SoftwareInstance>()
                .eq(customerId != null, SoftwareInstance::getCustomerId, customerId)));
    }

    @RequirePerm("maint:equipment:edit")
    @PostMapping
    public Result<SoftwareInstance> create(@Valid @RequestBody SoftwareInstance software) {
        softwareInstanceMapper.insert(software);
        return Result.ok(software);
    }

    /** 升级版本（安装软件工单完工后维护） */
    @RequirePerm("maint:equipment:edit")
    @PostMapping("/{id}/version")
    public Result<SoftwareInstance> updateVersion(@PathVariable Long id, @RequestParam String version) {
        SoftwareInstance software = softwareInstanceMapper.selectById(id);
        if (software == null) {
            return Result.fail("软件实例不存在: id=" + id);
        }
        software.setVersion(version);
        softwareInstanceMapper.updateById(software);
        return Result.ok(software);
    }
}
