package com.fixing.inventory.service;

import com.fixing.common.BusinessException;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import org.springframework.stereotype.Service;

/**
 * 库存服务：扣库存 + 记领料流水。
 *
 * <p>注意这里没有加 @Transactional —— 本方法由 TicketService.usePart()
 * 在它的事务里调用，Spring 事务默认传播行为 REQUIRED 会让本方法加入调用方
 * 已开启的事务，保证"扣库存 + 写流水 + 写工单日志"三件事同生共死。
 */
@Service
public class InventoryService {

    private final SparePartMapper sparePartMapper;
    private final SparePartUsageMapper usageMapper;

    public InventoryService(SparePartMapper sparePartMapper, SparePartUsageMapper usageMapper) {
        this.sparePartMapper = sparePartMapper;
        this.usageMapper = usageMapper;
    }

    /**
     * 在某工单上使用备件：原子扣库存，成功后记一条领料流水。
     *
     * @throws BusinessException 备件不存在或库存不足（调用方事务会随之回滚）
     */
    public SparePartUsage useOnTicket(Long partId, int qty, Long engineerId, Long ticketId, Long equipmentId) {
        if (qty <= 0) {
            throw new BusinessException("领用数量必须大于 0");
        }
        // 原子扣减：WHERE 里带 stock_qty >= qty，返回 0 行即失败
        int affected = sparePartMapper.deductStock(partId, qty);
        if (affected == 0) {
            // 扣减失败：区分"备件不存在"和"库存不足"，给出能看懂的报错
            SparePart part = sparePartMapper.selectById(partId);
            if (part == null) {
                throw new BusinessException("备件不存在: id=" + partId);
            }
            throw new BusinessException("库存不足: " + part.getName()
                    + " 现有 " + part.getStockQty() + "，需要 " + qty);
        }
        // 扣成功了才记流水（同一事务内，任何一步失败都整体回滚）
        SparePartUsage usage = new SparePartUsage();
        usage.setPartId(partId);
        usage.setQty(qty);
        usage.setEngineerId(engineerId);
        usage.setTicketId(ticketId);
        usage.setEquipmentId(equipmentId);
        usageMapper.insert(usage);
        return usage;
    }
}
