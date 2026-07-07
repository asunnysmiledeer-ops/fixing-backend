package com.fixing.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.platform.domain.SysFeature;
import com.fixing.platform.mapper.SysFeatureMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 功能开关服务。业务代码在功能入口处问一句 isEnabled("resident_engineer")，
 * 平台端一键启停 —— "要不要上某个功能"从发版决策变成运营决策。
 */
@Service
public class FeatureService {

    private final SysFeatureMapper featureMapper;

    public FeatureService(SysFeatureMapper featureMapper) {
        this.featureMapper = featureMapper;
    }

    public boolean isEnabled(String featureKey) {
        SysFeature f = featureMapper.selectOne(new LambdaQueryWrapper<SysFeature>()
                .eq(SysFeature::getFeatureKey, featureKey));
        return f != null && Boolean.TRUE.equals(f.getEnabled());
    }

    /** 当前启用的功能键列表（登录后发给前端，控制入口显隐） */
    public List<String> enabledKeys() {
        return featureMapper.selectList(new LambdaQueryWrapper<SysFeature>()
                        .eq(SysFeature::getEnabled, true))
                .stream().map(SysFeature::getFeatureKey).toList();
    }
}
