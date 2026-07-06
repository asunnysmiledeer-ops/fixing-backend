package com.fixing.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.common.BusinessException;
import com.fixing.customer.mapper.CustomerMapper;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.service.InventoryService;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketLog;
import com.fixing.ticket.dto.*;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.enums.TicketType;
import com.fixing.ticket.mapper.TicketLogMapper;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.ticket.priority.PriorityDecider;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工单服务 —— 全部业务规则收在这一层（三层架构的中间层）。
 *
 * <p>三条铁律（工程红线）：
 * 1. 状态校验只在 Service：不信 Controller、更不信前端传来的状态；
 * 2. 每个动作方法都是同一个套路：查工单 → 校验操作人权限 → 校验状态跳转 →
 *    改状态+盖时间戳 → 写一条 TicketLog；
 * 3. @Transactional 只放在 Service 的 public 方法上（Spring 用代理实现事务，
 *    自调用/非 public 方法上的注解不生效），事务里不做远程调用。
 */
@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TicketLogMapper ticketLogMapper;
    private final SysUserMapper sysUserMapper;
    private final CustomerMapper customerMapper;
    private final EquipmentMapper equipmentMapper;
    private final InventoryService inventoryService;
    private final PriorityDecider priorityDecider;

    /**
     * 构造器注入所有依赖。注意 PriorityDecider 注入的是接口 ——
     * 现在容器里只有 RulePriorityDecider 这个实现，将来加 AI 实现时
     * 用 @Primary/@Qualifier 切换，本类一行不改（给易变决策留的那扇门）。
     */
    public TicketService(TicketMapper ticketMapper,
                         TicketLogMapper ticketLogMapper,
                         SysUserMapper sysUserMapper,
                         CustomerMapper customerMapper,
                         EquipmentMapper equipmentMapper,
                         InventoryService inventoryService,
                         PriorityDecider priorityDecider) {
        this.ticketMapper = ticketMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.customerMapper = customerMapper;
        this.equipmentMapper = equipmentMapper;
        this.inventoryService = inventoryService;
        this.priorityDecider = priorityDecider;
    }

    // ══════════════════════════ 查询 ══════════════════════════

    /** 工单详情 */
    public Ticket getById(Long id) {
        return requireTicket(id);
    }

    /** 工单流转记录（客户查进度靠它） */
    public List<TicketLog> getLogs(Long ticketId) {
        requireTicket(ticketId); // 工单不存在时明确报错，而不是静默返回空列表
        return ticketLogMapper.selectList(new LambdaQueryWrapper<TicketLog>()
                .eq(TicketLog::getTicketId, ticketId)
                .orderByAsc(TicketLog::getId));
    }

    /** 工单列表，按 状态/优先级/工程师/客户 组合筛选（都可空） */
    public List<Ticket> list(TicketStatus status, Priority priority, Long engineerId, Long customerId) {
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<>();
        // Wrapper 的 condition 重载：第一个参数为 false 时跳过该条件，省去一堆 if
        query.eq(status != null, Ticket::getStatus, status)
             .eq(priority != null, Ticket::getPriority, priority)
             .eq(engineerId != null, Ticket::getAssignedEngineerId, engineerId)
             .eq(customerId != null, Ticket::getCustomerId, customerId)
             .orderByDesc(Ticket::getCreatedAt);
        return ticketMapper.selectList(query);
    }

    // ══════════════════════════ UC1 提交报修 ══════════════════════════

    @Transactional
    public Ticket create(TicketCreateDTO dto) {
        SysUser operator = requireUser(dto.getOperatorId());
        // 报修入口给客户和管理员（管理员代客户录单是常见场景）
        requireRole(operator, UserRole.CUSTOMER, UserRole.ADMIN);

        // 外键指向的记录必须真实存在 —— 别等插入后靠脏数据坑自己
        if (customerMapper.selectById(dto.getCustomerId()) == null) {
            throw new BusinessException("客户不存在: id=" + dto.getCustomerId());
        }
        if (dto.getType() == TicketType.HARDWARE) {
            // 硬件工单必须挂设备（软件工单 v0 允许不挂）
            if (dto.getEquipmentId() == null) {
                throw new BusinessException("硬件工单必须选择设备");
            }
            if (equipmentMapper.selectById(dto.getEquipmentId()) == null) {
                throw new BusinessException("设备不存在: id=" + dto.getEquipmentId());
            }
        }

        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setCustomerId(dto.getCustomerId());
        ticket.setEquipmentId(dto.getEquipmentId());
        ticket.setType(dto.getType());
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        // 优先级交给 PriorityDecider 判定，业务代码不关心它背后是规则还是 AI
        ticket.setPriority(priorityDecider.decide(dto));
        ticket.setStatus(TicketStatus.PENDING_ASSIGN); // 新工单一律从"待派单"开始
        ticket.setContactName(dto.getContactName());
        ticket.setContactPhone(dto.getContactPhone());
        ticket.setCreatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);

        // 新建也记一条流转日志（fromStatus=null 表示"从无到有"）
        writeLog(ticket.getId(), null, TicketStatus.PENDING_ASSIGN, "create", operator, dto.getTitle());
        return ticket;
    }

    // ══════════════════════════ UC5 派单 / UC6 改派 ══════════════════════════

    /** 派单：待派单 → 已派单，只有管理员能做 */
    @Transactional
    public Ticket assign(Long ticketId, TicketAssignDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireRole(operator, UserRole.ADMIN);
        SysUser engineer = requireEngineer(dto.getEngineerId());

        // 派单只允许从"待派单"出发（改派走 reassign，语义分开、规则也分开）
        requireCurrentStatus(ticket, TicketStatus.PENDING_ASSIGN);
        changeStatus(ticket, TicketStatus.ASSIGNED, operator, "assign",
                "派给工程师: " + engineer.getRealName());
        ticket.setAssignedEngineerId(engineer.getId());
        ticket.setAssignedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        // v0 通知用 mock：真实场景这里应发订阅消息/短信（且要放在事务外或用消息队列）
        System.out.println("[通知mock] 工程师 " + engineer.getRealName()
                + " 你有新工单: " + ticket.getTicketNo());
        return ticket;
    }

    /** 改派：已派单/处理中 → 已派单（换人），只有管理员能做（SLA 超时重派也走这里） */
    @Transactional
    public Ticket reassign(Long ticketId, TicketAssignDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireRole(operator, UserRole.ADMIN);
        SysUser engineer = requireEngineer(dto.getEngineerId());

        // 状态机里 ASSIGNED→ASSIGNED / IN_PROGRESS→ASSIGNED 均合法，其余会被 changeStatus 拒绝
        changeStatus(ticket, TicketStatus.ASSIGNED, operator, "reassign",
                "改派给工程师: " + engineer.getRealName()
                        + (dto.getRemark() == null ? "" : "；原因: " + dto.getRemark()));
        ticket.setAssignedEngineerId(engineer.getId());
        ticket.setAssignedAt(LocalDateTime.now());
        // startedAt 不清空：它记录的是"首次开始处理"的事实，历史不改写
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════════════════ UC7 接单 / UC8 完工 ══════════════════════════

    /** 接单：已派单 → 处理中，只有"被派的那位"工程师能接（越权=403 思想） */
    @Transactional
    public Ticket accept(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireAssignedEngineer(ticket, operator); // 工程师B 不能接派给 工程师A 的单

        changeStatus(ticket, TicketStatus.IN_PROGRESS, operator, "accept", dto.getRemark());
        ticket.setStartedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /** 完工：处理中 → 待确认，只有责任工程师能提交 */
    @Transactional
    public Ticket complete(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireAssignedEngineer(ticket, operator);

        changeStatus(ticket, TicketStatus.PENDING_CONFIRM, operator, "complete",
                dto.getRemark() == null ? "维修完成" : dto.getRemark());
        ticket.setCompletedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════════════════ UC3 确认 / 驳回 ══════════════════════════

    /** 确认完工：待确认 → 已完成（终态），客户操作 */
    @Transactional
    public Ticket confirm(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        // v0 只校验角色是客户；"是否本工单的客户"要等 v1 用户和客户关联后才能校验
        requireRole(operator, UserRole.CUSTOMER);

        changeStatus(ticket, TicketStatus.COMPLETED, operator, "confirm", dto.getRemark());
        LocalDateTime now = LocalDateTime.now();
        ticket.setConfirmedAt(now);
        ticket.setClosedAt(now);
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /** 驳回（没修好）：待确认 → 处理中，客户操作，工程师返工 */
    @Transactional
    public Ticket reject(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireRole(operator, UserRole.CUSTOMER);

        changeStatus(ticket, TicketStatus.IN_PROGRESS, operator, "reject",
                dto.getRemark() == null ? "客户驳回" : "驳回原因: " + dto.getRemark());
        // completedAt 不清空：保留"曾经交付过一次"的痕迹，返工时长可以由日志算出
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════════════════ UC4 取消 ══════════════════════════

    /**
     * 取消：→ 已取消（终态）。
     * 权限规则（M4 详设）：客户只能在"待派单"时撤回；管理员在"待派单/已派单"都可取消。
     */
    @Transactional
    public Ticket cancel(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireRole(operator, UserRole.CUSTOMER, UserRole.ADMIN);

        if (operator.getRole() == UserRole.CUSTOMER
                && ticket.getStatus() != TicketStatus.PENDING_ASSIGN) {
            throw new BusinessException("已派单的工单客户不能自行取消，请联系管理员");
        }

        changeStatus(ticket, TicketStatus.CANCELLED, operator, "cancel",
                dto.getRemark() == null ? "取消报修" : "取消原因: " + dto.getRemark());
        ticket.setClosedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════════════════ E 阶段：换件扣库存 ══════════════════════════

    /**
     * 工程师在维修中使用备件：扣库存 + 记领料流水 + 记工单日志，三件事在同一个事务里。
     * 任何一步失败（库存不足抛 BusinessException）整体回滚 —— 不会出现
     * "库存扣了但没有流水"或"有流水但库存没动"的中间态。
     */
    @Transactional
    public SparePartUsage usePart(Long ticketId, UsePartDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = requireUser(dto.getOperatorId());
        requireAssignedEngineer(ticket, operator);

        // 只有"处理中"的工单能领料 —— 没接单不能领，完工了也不能补领
        requireCurrentStatus(ticket, TicketStatus.IN_PROGRESS);

        SparePartUsage usage = inventoryService.useOnTicket(
                dto.getPartId(), dto.getQty(), operator.getId(),
                ticket.getId(), ticket.getEquipmentId());

        // 换件不改状态，但值得留痕：from=to=IN_PROGRESS 的一条动作日志
        writeLog(ticket.getId(), TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS,
                "use_part", operator, "领用备件 id=" + dto.getPartId() + " x" + dto.getQty());
        return usage;
    }

    // ══════════════════════════ 私有工具方法 ══════════════════════════

    /**
     * 状态跳转的唯一入口：先问状态机"这步走得通吗"，走不通直接抛业务异常。
     * 所有动作方法都必须经过这里改状态 —— 保证非法跳转（如对已完成的单 accept）
     * 一律被拒，这就是"状态机是工单的法律"的落地点。
     */
    private void changeStatus(Ticket ticket, TicketStatus target,
                              SysUser operator, String action, String remark) {
        TicketStatus from = ticket.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new BusinessException("非法状态跳转: " + from + " → " + target
                    + "（动作: " + action + "）");
        }
        ticket.setStatus(target);
        writeLog(ticket.getId(), from, target, action, operator, remark);
    }

    /** 追加一条流转日志（审计流水，只增不改） */
    private void writeLog(Long ticketId, TicketStatus from, TicketStatus to,
                          String action, SysUser operator, String remark) {
        TicketLog log = new TicketLog();
        log.setTicketId(ticketId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setAction(action);
        log.setOperatorId(operator.getId());
        log.setOperatorRole(operator.getRole());
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        ticketLogMapper.insert(log);
    }

    /**
     * 生成业务工单号：FX + 日期 + 当日3位序号，如 FX20260706001。
     * 用"当日已有单数+1"实现，并发下可能撞号 —— 数据库 ticket_no 的 UNIQUE
     * 约束是兜底（撞了会报错重试）。Demo 够用；上量后换 Redis INCR 发号。
     */
    private String generateTicketNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long todayCount = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .likeRight(Ticket::getTicketNo, "FX" + datePart)); // likeRight = 'FX20260706%'
        return String.format("FX%s%03d", datePart, todayCount + 1);
    }

    /** 查工单，不存在就报错（别让 null 往下传） */
    private Ticket requireTicket(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在: id=" + id);
        }
        return ticket;
    }

    /** 查操作人，不存在就报错 */
    private SysUser requireUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: id=" + id);
        }
        return user;
    }

    /** 校验操作人角色在允许名单内 */
    private void requireRole(SysUser user, UserRole... allowed) {
        for (UserRole role : allowed) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw new BusinessException("无权操作: 用户 " + user.getUsername()
                + " 角色为 " + user.getRole());
    }

    /** 校验目标用户确实是工程师（防止把单派给客户/管理员） */
    private SysUser requireEngineer(Long engineerId) {
        SysUser engineer = requireUser(engineerId);
        if (engineer.getRole() != UserRole.ENGINEER) {
            throw new BusinessException("用户 " + engineer.getUsername() + " 不是工程师，不能接单");
        }
        return engineer;
    }

    /** 校验操作人就是工单的责任工程师（验收标准第3条：工程师B不能动A的单） */
    private void requireAssignedEngineer(Ticket ticket, SysUser operator) {
        requireRole(operator, UserRole.ENGINEER);
        if (!operator.getId().equals(ticket.getAssignedEngineerId())) {
            throw new BusinessException("无权操作: 该工单的责任工程师不是你");
        }
    }

    /** 有些动作除了状态机还要求"必须正处于某状态"（语义更严），用这个断言 */
    private void requireCurrentStatus(Ticket ticket, TicketStatus expected) {
        if (ticket.getStatus() != expected) {
            throw new BusinessException("当前状态为 " + ticket.getStatus()
                    + "，该操作要求状态为 " + expected);
        }
    }
}
