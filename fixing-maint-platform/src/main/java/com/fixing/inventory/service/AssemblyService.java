package com.fixing.inventory.service;

import com.fixing.common.BusinessException;
import com.fixing.inventory.domain.AssemblyPart;
import com.fixing.inventory.domain.AssemblyRecord;
import com.fixing.inventory.domain.MachineStock;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.mapper.AssemblyPartMapper;
import com.fixing.inventory.mapper.AssemblyRecordMapper;
import com.fixing.inventory.mapper.MachineStockMapper;
import com.fixing.inventory.mapper.SparePartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 组装服务：配件 → 整机（一个事务里：逐项原子扣配件 → 整机 +N → 记组装流水）。
 * 任何一种配件不足，整单回滚 —— 不会出现"配件扣了机器没进库"。
 */
@Service
public class AssemblyService {

    private final MachineStockMapper machineStockMapper;
    private final SparePartMapper sparePartMapper;
    private final AssemblyRecordMapper assemblyRecordMapper;
    private final AssemblyPartMapper assemblyPartMapper;

    public AssemblyService(MachineStockMapper machineStockMapper, SparePartMapper sparePartMapper,
                           AssemblyRecordMapper assemblyRecordMapper, AssemblyPartMapper assemblyPartMapper) {
        this.machineStockMapper = machineStockMapper;
        this.sparePartMapper = sparePartMapper;
        this.assemblyRecordMapper = assemblyRecordMapper;
        this.assemblyPartMapper = assemblyPartMapper;
    }

    /**
     * @param parts [{partId, qty}] 本次组装消耗的配件清单（手选，用户定案）
     */
    @Transactional
    public AssemblyRecord assemble(Long machineStockId, int machineQty,
                                   List<Map<String, Object>> parts, String remark) {
        if (machineQty <= 0) {
            throw new BusinessException("组装台数必须大于 0");
        }
        MachineStock stock = machineStockMapper.selectById(machineStockId);
        if (stock == null) {
            throw new BusinessException("机型不存在: id=" + machineStockId);
        }

        AssemblyRecord record = new AssemblyRecord();
        record.setMachineStockId(machineStockId);
        record.setQty(machineQty);
        record.setRemark(remark);
        assemblyRecordMapper.insert(record);

        // 逐项原子扣配件 + 记消耗明细（含单价快照 → 组装成本可算）
        if (parts != null) {
            for (Map<String, Object> item : parts) {
                Long partId = Long.valueOf(item.get("partId").toString());
                int qty = Integer.parseInt(item.get("qty").toString());
                SparePart part = sparePartMapper.selectById(partId);
                if (part == null) {
                    throw new BusinessException("备件不存在: id=" + partId);
                }
                if (sparePartMapper.deductStock(partId, qty) == 0) {
                    throw new BusinessException("配件不足: " + part.getName()
                            + " 现有 " + part.getStockQty() + "，组装需要 " + qty);
                }
                AssemblyPart detail = new AssemblyPart();
                detail.setAssemblyId(record.getId());
                detail.setPartId(partId);
                detail.setQty(qty);
                detail.setUnitPrice(part.getUnitPrice());
                assemblyPartMapper.insert(detail);
            }
        }
        machineStockMapper.addQty(machineStockId, machineQty); // 整机入库
        return record;
    }
}
