package com.fixing.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.common.BusinessException;
import com.fixing.customer.mapper.CustomerMapper;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.domain.SoftwareInstance;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.equipment.mapper.SoftwareInstanceMapper;
import com.fixing.inventory.domain.MachineStock;
import com.fixing.inventory.mapper.MachineStockMapper;
import com.fixing.order.domain.SalesOrder;
import com.fixing.order.mapper.SalesOrderMapper;
import com.fixing.ticket.dto.TicketCreateDTO;
import com.fixing.ticket.enums.TicketType;
import com.fixing.ticket.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 订单服务：超管录单 → 管理员派发。
 *
 * <p>派发是一个事务里的连环动作（任何一步失败整体回滚）：
 * 整机订单：扣整机库存 → 按序列号生成客户设备档案(运送时间=今天) → 自动建"添加机器"安装工单
 * 软件订单：登记软件实例 → 自动建"安装软件"工单
 * 装完机器/软件后，这些新设备自然进入维保体系（查询系统/动态阈值/合同绑定都认它们）。
 */
@Service
public class OrderService {

    private final SalesOrderMapper orderMapper;
    private final MachineStockMapper machineStockMapper;
    private final EquipmentMapper equipmentMapper;
    private final SoftwareInstanceMapper softwareInstanceMapper;
    private final CustomerMapper customerMapper;
    private final TicketService ticketService;

    public OrderService(SalesOrderMapper orderMapper, MachineStockMapper machineStockMapper,
                        EquipmentMapper equipmentMapper, SoftwareInstanceMapper softwareInstanceMapper,
                        CustomerMapper customerMapper, TicketService ticketService) {
        this.orderMapper = orderMapper;
        this.machineStockMapper = machineStockMapper;
        this.equipmentMapper = equipmentMapper;
        this.softwareInstanceMapper = softwareInstanceMapper;
        this.customerMapper = customerMapper;
        this.ticketService = ticketService;
    }

    public List<SalesOrder> list(String status) {
        return orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(status != null, SalesOrder::getStatus, status)
                .orderByDesc(SalesOrder::getCreateTime));
    }

    /** 超管录单（Controller 用 @RequirePerm(maint:order:edit) 挡权限） */
    @Transactional
    public SalesOrder create(SalesOrder order) {
        if (customerMapper.selectById(order.getCustomerId()) == null) {
            throw new BusinessException("客户不存在: id=" + order.getCustomerId());
        }
        if ("MACHINE".equals(order.getOrderType())) {
            if (order.getModel() == null || order.getQty() == null || order.getQty() <= 0) {
                throw new BusinessException("整机订单必须指定机型和数量");
            }
        } else if ("SOFTWARE".equals(order.getOrderType())) {
            if (order.getSoftwareName() == null) {
                throw new BusinessException("软件订单必须指定软件名");
            }
            order.setQty(1);
        } else {
            throw new BusinessException("订单类型必须是 MACHINE 或 SOFTWARE");
        }
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING");
        orderMapper.insert(order);
        return order;
    }

    /**
     * 派发整机订单：serialNos 数量必须等于订单台数（每台机器一个序列号，进设备档案）。
     */
    @Transactional
    public SalesOrder dispatchMachine(Long orderId, List<String> serialNos, String location) {
        SalesOrder order = requirePending(orderId);
        if (!"MACHINE".equals(order.getOrderType())) {
            throw new BusinessException("该订单不是整机订单");
        }
        if (serialNos == null || serialNos.size() != order.getQty()) {
            throw new BusinessException("需要为每台机器提供序列号（应 " + order.getQty() + " 个）");
        }
        MachineStock stock = machineStockMapper.selectOne(new LambdaQueryWrapper<MachineStock>()
                .eq(MachineStock::getModel, order.getModel()));
        if (stock == null) {
            throw new BusinessException("整机库存中没有机型 " + order.getModel() + "，请先组装");
        }
        // 原子扣整机库存：不足直接失败（先去组装）
        if (machineStockMapper.deductQty(stock.getId(), order.getQty()) == 0) {
            throw new BusinessException("整机库存不足: " + order.getModel()
                    + " 现有 " + stock.getQty() + " 台，订单需要 " + order.getQty() + " 台，请先组装");
        }
        // 生成客户设备档案（运送时间=今天，从此进入维保体系）
        for (String serialNo : serialNos) {
            Equipment equipment = new Equipment();
            equipment.setCustomerId(order.getCustomerId());
            equipment.setEquipmentType(stock.getEquipmentType());
            equipment.setModel(order.getModel());
            equipment.setSerialNo(serialNo);
            equipment.setLocation(location);
            equipment.setStatus("NORMAL");
            equipment.setDeliveredAt(LocalDate.now());
            equipmentMapper.insert(equipment); // 序列号重复由 UNIQUE 约束兜底报错
        }
        // 自动建"添加机器"安装工单（待派单队列，管理员随后指派工程师上门）
        TicketCreateDTO ticket = new TicketCreateDTO();
        ticket.setCustomerId(order.getCustomerId());
        ticket.setType(TicketType.INSTALL);
        ticket.setTitle("新机安装：" + order.getModel() + " ×" + order.getQty()
                + "（订单 " + order.getOrderNo() + "）");
        ticket.setDescription("序列号: " + String.join(", ", serialNos)
                + (location == null ? "" : "；安装位置: " + location));
        ticketService.create(ticket);

        order.setStatus("DISPATCHED");
        orderMapper.updateById(order);
        return order;
    }

    /** 派发软件订单：登记软件实例 + 自动建"安装软件"工单 */
    @Transactional
    public SalesOrder dispatchSoftware(Long orderId, Long equipmentId) {
        SalesOrder order = requirePending(orderId);
        if (!"SOFTWARE".equals(order.getOrderType())) {
            throw new BusinessException("该订单不是软件订单");
        }
        SoftwareInstance software = new SoftwareInstance();
        software.setCustomerId(order.getCustomerId());
        software.setEquipmentId(equipmentId);
        software.setName(order.getSoftwareName());
        software.setVersion(order.getSoftwareVersion());
        softwareInstanceMapper.insert(software);

        TicketCreateDTO ticket = new TicketCreateDTO();
        ticket.setCustomerId(order.getCustomerId());
        ticket.setType(TicketType.SOFTWARE_INSTALL);
        ticket.setSoftwareInstanceId(software.getId());
        ticket.setTitle("软件部署：" + order.getSoftwareName() + " " +
                (order.getSoftwareVersion() == null ? "" : order.getSoftwareVersion())
                + "（订单 " + order.getOrderNo() + "）");
        ticketService.create(ticket);

        order.setStatus("DISPATCHED");
        orderMapper.updateById(order);
        return order;
    }

    private SalesOrder requirePending(Long id) {
        SalesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在: id=" + id);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException("该订单已派发过");
        }
        return order;
    }

    private String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .likeRight(SalesOrder::getOrderNo, "SO" + datePart));
        return String.format("SO%s%03d", datePart, count + 1);
    }
}
