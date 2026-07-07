package com.fixing.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.platform.domain.SysParam;
import com.fixing.platform.mapper.SysParamMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 业务参数服务：取代写死在 yml 的数值（上门费/维修费/提醒天数…）。
 * 平台端改完即刻生效。查不到时用调用方给的默认值兜底 —— 参数表被清空也不至于崩。
 */
@Service
public class ParamService {

    private final SysParamMapper paramMapper;

    public ParamService(SysParamMapper paramMapper) {
        this.paramMapper = paramMapper;
    }

    public String get(String key, String defaultValue) {
        SysParam p = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamKey, key));
        return p == null || p.getParamValue() == null ? defaultValue : p.getParamValue();
    }

    public BigDecimal getDecimal(String key, String defaultValue) {
        return new BigDecimal(get(key, defaultValue));
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
