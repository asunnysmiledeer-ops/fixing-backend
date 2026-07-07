package com.fixing.web.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.platform.domain.SysDict;
import com.fixing.platform.domain.SysFeature;
import com.fixing.platform.domain.SysParam;
import com.fixing.platform.mapper.SysDictMapper;
import com.fixing.platform.mapper.SysFeatureMapper;
import com.fixing.platform.mapper.SysParamMapper;
import com.fixing.platform.service.DictService;
import com.fixing.platform.service.FeatureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台·配置中心："添加/调整功能不发版"的三件套。
 * ① 功能开关：新功能一键启停（如驻场工程师模式）
 * ② 业务参数：上门费/维修费/提醒天数等，改完即刻生效
 * ③ 数据字典：客户类型等可选值，增删一条=全平台下拉多/少一项
 */
@RestController
@RequestMapping("/platform")
public class PlatformConfigController {

    private final SysFeatureMapper featureMapper;
    private final SysParamMapper paramMapper;
    private final SysDictMapper dictMapper;
    private final FeatureService featureService;
    private final DictService dictService;

    public PlatformConfigController(SysFeatureMapper featureMapper, SysParamMapper paramMapper,
                                    SysDictMapper dictMapper, FeatureService featureService,
                                    DictService dictService) {
        this.featureMapper = featureMapper;
        this.paramMapper = paramMapper;
        this.dictMapper = dictMapper;
        this.featureService = featureService;
        this.dictService = dictService;
    }

    // ── 功能开关 ──

    @RequirePerm("platform:config:list")
    @GetMapping("/features")
    public Result<List<SysFeature>> features() {
        return Result.ok(featureMapper.selectList(null));
    }

    @RequirePerm("platform:config:edit")
    @PostMapping("/features/{id}/toggle")
    public Result<SysFeature> toggleFeature(@PathVariable Long id) {
        SysFeature f = featureMapper.selectById(id);
        if (f == null) {
            throw new BusinessException("功能不存在: id=" + id);
        }
        f.setEnabled(!Boolean.TRUE.equals(f.getEnabled()));
        featureMapper.updateById(f);
        return Result.ok(f);
    }

    /** 当前启用的功能键（任何登录者可查 —— 前端据此显隐入口，无需权限） */
    @GetMapping("/features/enabled")
    public Result<List<String>> enabledFeatures() {
        return Result.ok(featureService.enabledKeys());
    }

    // ── 业务参数 ──

    @RequirePerm("platform:config:list")
    @GetMapping("/params")
    public Result<List<SysParam>> params() {
        return Result.ok(paramMapper.selectList(null));
    }

    @RequirePerm("platform:config:edit")
    @PostMapping("/params/{id}")
    public Result<SysParam> updateParam(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SysParam p = paramMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("参数不存在: id=" + id);
        }
        p.setParamValue(body.get("value"));
        paramMapper.updateById(p);
        return Result.ok(p);
    }

    // ── 数据字典 ──

    /** 按类型取字典（任何登录者可查 —— 各处下拉框的数据源） */
    @GetMapping("/dicts")
    public Result<List<SysDict>> dicts(@RequestParam String type) {
        return Result.ok(dictService.byType(type));
    }

    @RequirePerm("platform:config:list")
    @GetMapping("/dicts/all")
    public Result<List<SysDict>> allDicts() {
        return Result.ok(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .orderByAsc(SysDict::getDictType).orderByAsc(SysDict::getSort)));
    }

    @RequirePerm("platform:config:edit")
    @PostMapping("/dicts")
    public Result<SysDict> addDict(@RequestBody SysDict dict) {
        if (dict.getSort() == null) {
            dict.setSort(0);
        }
        dictMapper.insert(dict);
        return Result.ok(dict);
    }

    @RequirePerm("platform:config:edit")
    @PostMapping("/dicts/{id}/delete")
    public Result<Void> deleteDict(@PathVariable Long id) {
        dictMapper.deleteById(id); // 软删（@TableLogic）
        return Result.ok();
    }
}
