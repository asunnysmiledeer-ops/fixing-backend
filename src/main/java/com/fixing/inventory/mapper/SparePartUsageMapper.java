package com.fixing.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.inventory.domain.SparePartUsage;

/** 领料记录 Mapper：只增不改（流水表的天性），BaseMapper 够用。 */
public interface SparePartUsageMapper extends BaseMapper<SparePartUsage> {
}
