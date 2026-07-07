package com.fixing.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.customer.domain.Customer;
import com.fixing.customer.mapper.CustomerMapper;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.domain.SoftwareInstance;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.equipment.mapper.SoftwareInstanceMapper;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备台账 + 设备查询系统（M1.5 增强）。
 *
 * <p>三个能力：
 * 1. 多条件筛选：序列号(模糊)/运送时间区间/客户/设备类型，结果附**维修次数**；
 * 2. 设备档案聚合（/profile）：基本信息 + 适用部件 + 已装软件 + 维修统计与原因 + 换件历史 ——
 *    工程师接单后看这一个接口就了解整台机器；
 * 3. 数据隔离照旧：客户只能查/看自己单位的设备。
 */
@RestController
@RequestMapping("/equipments")
public class EquipmentController {

    private final EquipmentMapper equipmentMapper;
    private final TicketMapper ticketMapper;
    private final CustomerMapper customerMapper;
    private final SoftwareInstanceMapper softwareInstanceMapper;
    private final SparePartMapper sparePartMapper;
    private final SparePartUsageMapper usageMapper;

    public EquipmentController(EquipmentMapper equipmentMapper, TicketMapper ticketMapper,
                               CustomerMapper customerMapper, SoftwareInstanceMapper softwareInstanceMapper,
                               SparePartMapper sparePartMapper, SparePartUsageMapper usageMapper) {
        this.equipmentMapper = equipmentMapper;
        this.ticketMapper = ticketMapper;
        this.customerMapper = customerMapper;
        this.softwareInstanceMapper = softwareInstanceMapper;
        this.sparePartMapper = sparePartMapper;
        this.usageMapper = usageMapper;
    }

    @RequirePerm("maint:equipment:edit")
    @PostMapping
    public Result<Equipment> create(@Valid @RequestBody Equipment equipment) {
        if (equipment.getStatus() == null) {
            equipment.setStatus("NORMAL");
        }
        equipmentMapper.insert(equipment);
        return Result.ok(equipment);
    }

    /**
     * 设备查询：序列号模糊 + 运送时间区间 + 客户 + 类型，全部可选叠加。
     * 每台设备附 repairCount（维修类工单数）—— 一次分组统计，不做 N+1。
     */
    @RequirePerm("maint:equipment:list")
    @GetMapping
    public Result<List<Equipment>> search(
            @RequestParam(required = false) String serialNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveredTo,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String equipmentType) {
        SysUser me = UserContext.current();
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.CUSTOMER) {
            query.eq(Equipment::getCustomerId, me.getCustomerId()); // 客户只查自己单位
        } else if (customerId != null) {
            query.eq(Equipment::getCustomerId, customerId);
        }
        query.like(serialNo != null && !serialNo.isBlank(), Equipment::getSerialNo, serialNo)
             .ge(deliveredFrom != null, Equipment::getDeliveredAt, deliveredFrom)
             .le(deliveredTo != null, Equipment::getDeliveredAt, deliveredTo)
             .eq(equipmentType != null && !equipmentType.isBlank(), Equipment::getEquipmentType, equipmentType)
             .orderByAsc(Equipment::getId);
        List<Equipment> list = equipmentMapper.selectList(query);

        // 维修次数：一次查全量维修工单再内存分组（Demo 量级；上量换 GROUP BY SQL）
        Map<Long, Long> counts = repairCountByEquipment();
        list.forEach(e -> e.setRepairCount(counts.getOrDefault(e.getId(), 0L)));
        return Result.ok(list);
    }

    /**
     * 设备档案：工程师接单后了解整台机器的一站式接口。
     * 内容 = 基本信息 + 客户 + 适用部件(按设备类型匹配) + 已装软件 + 维修统计与原因 + 换件历史。
     */
    @RequirePerm("maint:equipment:list")
    @GetMapping("/{id}/profile")
    public Result<Map<String, Object>> profile(@PathVariable Long id) {
        Equipment equipment = requireVisible(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipment", equipment);

        Customer customer = customerMapper.selectById(equipment.getCustomerId());
        out.put("customerName", customer == null ? null : customer.getName());
        out.put("customerAddress", customer == null ? null : customer.getAddress());

        // 适用部件（BOM 视角）：按设备类型匹配的备件 + 当前库存（工程师出门前判断带什么件）
        out.put("applicableParts", sparePartMapper.selectList(new LambdaQueryWrapper<SparePart>()
                .eq(SparePart::getEquipmentType, equipment.getEquipmentType())));

        // 已装软件
        out.put("softwares", softwareInstanceMapper.selectList(new LambdaQueryWrapper<SoftwareInstance>()
                .eq(SoftwareInstance::getEquipmentId, id)));

        // 维修统计与原因：维修类工单列表（标题即报修原因），次数即列表长度
        List<Ticket> repairs = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEquipmentId, id)
                .in(Ticket::getType, "HARDWARE", "SOFTWARE")
                .orderByDesc(Ticket::getCreateTime));
        out.put("repairCount", repairs.size());
        out.put("repairTickets", repairs.stream().map(t -> Map.of(
                "id", t.getId(), "ticketNo", t.getTicketNo(), "title", t.getTitle(),
                "type", t.getType().name(), "status", t.getStatus().name(),
                "createTime", String.valueOf(t.getCreateTime()))).toList());

        // 换件历史（部件更换记录，带备件名）
        List<SparePartUsage> usages = usageMapper.selectList(new LambdaQueryWrapper<SparePartUsage>()
                .eq(SparePartUsage::getEquipmentId, id)
                .orderByDesc(SparePartUsage::getCreateTime));
        Map<Long, String> partNames = new LinkedHashMap<>();
        sparePartMapper.selectList(null).forEach(p -> partNames.put(p.getId(), p.getName()));
        out.put("partUsages", usages.stream().map(u -> Map.of(
                "partName", partNames.getOrDefault(u.getPartId(), "备件#" + u.getPartId()),
                "qty", u.getQty(), "billable", u.getBillable(),
                "createTime", String.valueOf(u.getCreateTime()))).toList());
        return Result.ok(out);
    }

    /** 设备维修历史（旧接口保留，工单页跳转用） */
    @RequirePerm("maint:equipment:list")
    @GetMapping("/{id}/tickets")
    public Result<List<Ticket>> ticketHistory(@PathVariable Long id) {
        requireVisible(id);
        return Result.ok(ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getEquipmentId, id).orderByDesc(Ticket::getCreateTime)));
    }

    /** 维修类工单按设备分组计数 */
    private Map<Long, Long> repairCountByEquipment() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                        .isNotNull(Ticket::getEquipmentId)
                        .in(Ticket::getType, "HARDWARE", "SOFTWARE"))
                .forEach(t -> counts.merge(t.getEquipmentId(), 1L, Long::sum));
        return counts;
    }

    /** 客户只能访问自己单位的设备 */
    private Equipment requireVisible(Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException("设备不存在: id=" + id);
        }
        SysUser me = UserContext.current();
        if (me.getRole() == UserRole.CUSTOMER && !equipment.getCustomerId().equals(me.getCustomerId())) {
            throw new BusinessException("无权查看: 该设备不属于你的单位");
        }
        return equipment;
    }
}
