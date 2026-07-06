package com.fixing.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.inventory.domain.SparePart;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 备件 Mapper：BaseMapper 的 CRUD 之外，手写一条原子扣库存 SQL。
 */
public interface SparePartMapper extends BaseMapper<SparePart> {

    /**
     * 原子扣库存：条件 stock_qty >= 扣减数 写进 WHERE，
     * 数据库保证"检查 + 扣减"一步完成 —— 两个工程师同时领最后一件时，
     * 只有一个 UPDATE 能命中，另一个返回 0 行，不会把库存扣成负数。
     *
     * <p>如果先 SELECT 库存再在 Java 里判断、再 UPDATE，两步之间就有并发窗口（先检查后行动竞态）。
     *
     * <p>注意参数一律 #{}（预编译占位符，防 SQL 注入）；${} 是字符串拼接，绝不用于任何入参。
     *
     * @return 受影响行数：1=扣减成功，0=库存不足（或备件不存在）
     */
    @Update("UPDATE spare_part SET stock_qty = stock_qty - #{qty} " +
            "WHERE id = #{partId} AND stock_qty >= #{qty}")
    int deductStock(@Param("partId") Long partId, @Param("qty") int qty);
}
