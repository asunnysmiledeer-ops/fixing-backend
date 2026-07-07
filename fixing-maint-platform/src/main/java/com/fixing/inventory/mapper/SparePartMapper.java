package com.fixing.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.inventory.domain.SparePart;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 备件 Mapper：通用 CRUD 之外两条手写 SQL —— ①原子扣库存；②动态阈值列表。
 */
public interface SparePartMapper extends BaseMapper<SparePart> {

    /**
     * 原子扣库存：stock_qty >= 扣减数写进 WHERE，数据库保证"检查+扣减"一步完成，
     * 并发抢最后一件时只有一个 UPDATE 命中 —— 库存永远不为负。
     * （有这条原子 SQL，就不需要 Redis 分布式锁 —— 单库场景数据库自己就是锁。）
     *
     * @return 1=成功，0=库存不足或备件不存在
     */
    @Update("UPDATE spare_part SET stock_qty = stock_qty - #{qty} " +
            "WHERE id = #{partId} AND stock_qty >= #{qty} AND del_flag = '0'")
    int deductStock(@Param("partId") Long partId, @Param("qty") int qty);

    /** 原子入库（配件申请审批通过时加库存），与扣减同款写法 */
    @Update("UPDATE spare_part SET stock_qty = stock_qty + #{qty} " +
            "WHERE id = #{partId} AND del_flag = '0'")
    int addStock(@Param("partId") Long partId, @Param("qty") int qty);

    /**
     * 备件列表 + 动态阈值（M1：库存随签约设备自动调整）。
     *
     * <p>在保台数 = 该设备类型下，出现在"生效且未到期合同"的 contract_equipment
     * 明细里的设备数（DISTINCT 去重：同一台设备被多份合同覆盖只算一台）。
     * dynamic_threshold = max(人工阈值, 每台备货数 × 在保台数)。
     */
    @Select("SELECT p.*, " +
            "  IFNULL(c.cnt, 0) AS contracted_device_count, " +
            "  GREATEST(p.low_stock_threshold, p.per_device_qty * IFNULL(c.cnt, 0)) AS dynamic_threshold " +
            "FROM spare_part p " +
            "LEFT JOIN ( " +
            "  SELECT e.equipment_type, COUNT(DISTINCT e.id) AS cnt " +
            "  FROM equipment e " +
            "  JOIN contract_equipment ce ON ce.equipment_id = e.id " +
            "  JOIN contract ct ON ct.id = ce.contract_id " +
            "       AND ct.status = 'ACTIVE' AND ct.end_date >= CURDATE() AND ct.del_flag = '0' " +
            "  WHERE e.del_flag = '0' " +
            "  GROUP BY e.equipment_type " +
            ") c ON c.equipment_type = p.equipment_type " +
            "WHERE p.del_flag = '0' ORDER BY p.id")
    List<SparePart> selectListWithDynamicThreshold();
}
