package com.fixing.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.inventory.domain.MachineStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 整机库存 Mapper：原子加减（与备件同款写法，数据库就是锁）。 */
public interface MachineStockMapper extends BaseMapper<MachineStock> {

    /** 组装入库：整机 +qty */
    @Update("UPDATE machine_stock SET qty = qty + #{qty} WHERE id = #{id} AND del_flag = '0'")
    int addQty(@Param("id") Long id, @Param("qty") int qty);

    /** 派发出库：qty >= 扣减数 写进 WHERE，整机数量永不为负 */
    @Update("UPDATE machine_stock SET qty = qty - #{qty} WHERE id = #{id} AND qty >= #{qty} AND del_flag = '0'")
    int deductQty(@Param("id") Long id, @Param("qty") int qty);
}
