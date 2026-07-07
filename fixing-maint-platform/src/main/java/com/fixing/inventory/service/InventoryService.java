package com.fixing.inventory.service;

import com.fixing.common.BusinessException;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存服务：原子扣库存 + 记领料流水（带计费快照）。
 * 无 @Transactional：由 TicketService 在它的事务里调用（REQUIRED 传播加入调用方事务）。
 */
@Service
public class InventoryService {

    private final SparePartMapper partMapper;
    private final SparePartUsageMapper usageMapper;

    public InventoryService(SparePartMapper partMapper, SparePartUsageMapper usageMapper) {
        this.partMapper = partMapper;
        this.usageMapper = usageMapper;
    }

    /** 备件列表（带动态阈值：随在保签约设备数自动调整） */
    public List<SparePart> listWithDynamicThreshold() {
        return partMapper.selectListWithDynamicThreshold();
    }

    /** 低库存清单：库存 < 动态阈值 */
    public List<SparePart> lowStock() {
        return listWithDynamicThreshold().stream()
                .filter(p -> p.getStockQty() < p.getDynamicThreshold())
                .toList();
    }

    /**
     * 在工单上领用备件：原子扣减成功才记流水（含是否计费 + 单价快照）。
     *
     * @param billable 该次换件是否计费（由 TicketService 按合同免费清单判定后传入）
     */
    public SparePartUsage useOnTicket(Long partId, int qty, Long engineerId,
                                      Long ticketId, Long equipmentId, boolean billable) {
        if (qty <= 0) {
            throw new BusinessException("领用数量必须大于 0");
        }
        SparePart part = partMapper.selectById(partId);
        if (part == null) {
            throw new BusinessException("备件不存在: id=" + partId);
        }
        int affected = partMapper.deductStock(partId, qty);
        if (affected == 0) {
            throw new BusinessException("库存不足: " + part.getName()
                    + " 现有 " + part.getStockQty() + "，需要 " + qty);
        }
        SparePartUsage usage = new SparePartUsage();
        usage.setPartId(partId);
        usage.setQty(qty);
        usage.setEngineerId(engineerId);
        usage.setTicketId(ticketId);
        usage.setEquipmentId(equipmentId);
        usage.setBillable(billable);
        usage.setUnitPrice(part.getUnitPrice()); // 单价快照：将来调价不影响历史账
        usageMapper.insert(usage);
        return usage;
    }
}
