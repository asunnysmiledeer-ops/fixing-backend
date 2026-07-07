package com.fixing.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.platform.domain.SysDict;
import com.fixing.platform.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 字典服务：按类型取选项（前端下拉的数据源）。 */
@Service
public class DictService {

    private final SysDictMapper dictMapper;

    public DictService(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    public List<SysDict> byType(String dictType) {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getSort));
    }
}
