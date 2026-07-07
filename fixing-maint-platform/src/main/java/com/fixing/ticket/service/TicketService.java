package com.fixing.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.mapper.ContractPartMapper;
import com.fixing.contract.service.CoverageService;
import com.fixing.contract.vo.CoverageCard;
import com.fixing.customer.mapper.CustomerMapper;
import com.fixing.equipment.domain.Equipment;
import com.fixing.equipment.domain.SoftwareInstance;
import com.fixing.equipment.mapper.EquipmentMapper;
import com.fixing.equipment.mapper.SoftwareInstanceMapper;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.domain.SparePartUsage;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.inventory.mapper.SparePartUsageMapper;
import com.fixing.inventory.service.InventoryService;
import com.fixing.platform.service.ParamService;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketCharge;
import com.fixing.ticket.domain.TicketLog;
import com.fixing.ticket.dto.*;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.mapper.TicketChargeMapper;
import com.fixing.ticket.mapper.TicketLogMapper;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.ticket.priority.PriorityDecider;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 工单服务 —— 全部业务规则收在这一层。
 *
 * <p>M1 的三个升级点：
 * 1. 五类工单：维修类必传故障图，服务申请类不强制；设备/软件按类型分别校验；
 * 2. 在保快照：报修时点固化 covered/contractId（合同后来变动不影响已建工单计费）；
 * 3. 按次计费：不在保工单完工时自动生成结算单（上门费+计费配件+维修费）。
 *
 * <p>三条铁律不变：状态机是唯一跳转入口；每个动作写流转日志；
 * 入口权限在 Controller 的 @RequirePerm，本层只管"对象级"校验（是不是你的单）。
 */
@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TicketLogMapper logMapper;
    private final TicketChargeMapper chargeMapper;
    private final SysUserMapper sysUserMapper;
    private final CustomerMapper customerMapper;
    private final EquipmentMapper equipmentMapper;
    private final SoftwareInstanceMapper softwareInstanceMapper;
    private final SparePartMapper sparePartMapper;
    private final SparePartUsageMapper usageMapper;
    private final ContractMapper contractMapper;
    private final ContractPartMapper contractPartMapper;
    private final InventoryService inventoryService;
    private final CoverageService coverageService;
    private final PriorityDecider priorityDecider;
    private final ParamService paramService;

    public TicketService(TicketMapper ticketMapper, TicketLogMapper logMapper,
                         TicketChargeMapper chargeMapper, SysUserMapper sysUserMapper,
                         CustomerMapper customerMapper, EquipmentMapper equipmentMapper,
                         SoftwareInstanceMapper softwareInstanceMapper,
                         SparePartMapper sparePartMapper, SparePartUsageMapper usageMapper,
                         ContractMapper contractMapper, ContractPartMapper contractPartMapper,
                         InventoryService inventoryService, CoverageService coverageService,
                         PriorityDecider priorityDecider, ParamService paramService) {
        this.ticketMapper = ticketMapper;
        this.logMapper = logMapper;
        this.chargeMapper = chargeMapper;
        this.sysUserMapper = sysUserMapper;
        this.customerMapper = customerMapper;
        this.equipmentMapper = equipmentMapper;
        this.softwareInstanceMapper = softwareInstanceMapper;
        this.sparePartMapper = sparePartMapper;
        this.usageMapper = usageMapper;
        this.contractMapper = contractMapper;
        this.contractPartMapper = contractPartMapper;
        this.inventoryService = inventoryService;
        this.coverageService = coverageService;
        this.priorityDecider = priorityDecider;
        this.paramService = paramService;
    }

    // ══════════════ 查询（数据隔离） ══════════════

    /** 列表：先按登录角色圈定硬边界（工程师=自己的单/客户=本单位），再叠加筛选 */
    public List<Ticket> list(TicketStatus status, Priority priority, Long engineerId, Long customerId) {
        SysUser me = UserContext.current();
        LambdaQueryWrapper<Ticket> query = new LambdaQueryWrapper<>();
        switch (me.getRole()) {
            case ENGINEER -> query.eq(Ticket::getAssignedEngineerId, me.getId());
            case CUSTOMER -> query.eq(Ticket::getCustomerId, requireCustomerId(me));
            case ADMIN -> { /* 全量 */ }
        }
        query.eq(status != null, Ticket::getStatus, status)
             .eq(priority != null, Ticket::getPriority, priority)
             .eq(engineerId != null, Ticket::getAssignedEngineerId, engineerId)
             .eq(customerId != null, Ticket::getCustomerId, customerId)
             .orderByDesc(Ticket::getCreateTime);
        return ticketMapper.selectList(query);
    }

    public Ticket getById(Long id) {
        Ticket ticket = requireTicket(id);
        requireVisible(ticket, UserContext.current());
        return ticket;
    }

    public List<TicketLog> getLogs(Long ticketId) {
        requireVisible(requireTicket(ticketId), UserContext.current());
        return logMapper.selectList(new LambdaQueryWrapper<TicketLog>()
                .eq(TicketLog::getTicketId, ticketId).orderByAsc(TicketLog::getId));
    }

    /** 工单结算明细（不在保工单完工后生成；在保且全免费件时为空列表） */
    public List<TicketCharge> getCharges(Long ticketId) {
        requireVisible(requireTicket(ticketId), UserContext.current());
        return chargeMapper.selectList(new LambdaQueryWrapper<TicketCharge>()
                .eq(TicketCharge::getTicketId, ticketId).orderByAsc(TicketCharge::getId));
    }

    /**
     * 在保状态卡（派单/详情界面顶部）：以工单的"报修时点快照"为准，
     * 附带合同免费备件名单（工程师换件前能预判收不收费）。
     */
    public CoverageCard coverageCard(Long ticketId) {
        Ticket ticket = requireTicket(ticketId);
        requireVisible(ticket, UserContext.current());
        if (!Boolean.TRUE.equals(ticket.getCovered())) {
            return new CoverageCard(false, null, null, null, null, List.of(),
                    "不在保：按次收费（上门费 " + paramService.getDecimal("charge.visit_fee", "200")
                            + " 元 + 配件费 + 维修费）");
        }
        Contract contract = contractMapper.selectById(ticket.getContractId());
        List<Long> freePartIds = contractPartMapper.selectList(
                        new LambdaQueryWrapper<com.fixing.contract.domain.ContractPart>()
                                .eq(com.fixing.contract.domain.ContractPart::getContractId, ticket.getContractId()))
                .stream().map(com.fixing.contract.domain.ContractPart::getPartId).toList();
        List<String> freePartNames = freePartIds.isEmpty() ? List.of()
                : sparePartMapper.selectBatchIds(freePartIds).stream().map(SparePart::getName).toList();
        LocalDate end = contract == null ? null : contract.getEndDate();
        Long daysLeft = end == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), end);
        return new CoverageCard(true, ticket.getContractId(),
                contract == null ? null : contract.getName(), end, daysLeft, freePartNames,
                "合同内服务" + (freePartNames.isEmpty() ? "（换件另计费）" : "，免费件：" + String.join("、", freePartNames)));
    }

    // ══════════════ 提交报修/服务申请 ══════════════

    @Transactional
    public Ticket create(TicketCreateDTO dto) {
        SysUser operator = UserContext.current();
        // 归属单位：客户强制取登录账号关联单位；管理员代录可指定
        Long customerId = operator.getRole() == UserRole.CUSTOMER
                ? requireCustomerId(operator) : dto.getCustomerId();
        if (customerId == null || customerMapper.selectById(customerId) == null) {
            throw new BusinessException("客户不存在: id=" + customerId);
        }

        // 维修类客户提交必传故障图/视频；服务申请类（添加/移机/装软件）不强制
        if (operator.getRole() == UserRole.CUSTOMER && dto.getType().isRepair()
                && (dto.getPhotos() == null || dto.getPhotos().isEmpty())) {
            throw new BusinessException("维修报障必须上传机器或软件异常的图片/视频");
        }

        // 设备校验：需要挂设备的类型（硬件维修/移机）必填，且必须属于报修客户
        if (dto.getType().isEquipmentRequired() && dto.getEquipmentId() == null) {
            throw new BusinessException("该类型工单必须选择设备");
        }
        if (dto.getEquipmentId() != null) {
            Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
            if (equipment == null) {
                throw new BusinessException("设备不存在: id=" + dto.getEquipmentId());
            }
            if (!equipment.getCustomerId().equals(customerId)) {
                throw new BusinessException("该设备不属于此客户，无法提交");
            }
        }
        // 软件实例校验：同样必须属于报修客户
        if (dto.getSoftwareInstanceId() != null) {
            SoftwareInstance software = softwareInstanceMapper.selectById(dto.getSoftwareInstanceId());
            if (software == null || !software.getCustomerId().equals(customerId)) {
                throw new BusinessException("软件实例不存在或不属于此客户");
            }
        }

        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setCustomerId(customerId);
        ticket.setEquipmentId(dto.getEquipmentId());
        ticket.setSoftwareInstanceId(dto.getSoftwareInstanceId());
        ticket.setType(dto.getType());
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setPhotos(dto.getPhotos());
        ticket.setPriority(priorityDecider.decide(dto));
        ticket.setStatus(TicketStatus.PENDING_ASSIGN);
        ticket.setContactName(dto.getContactName());
        ticket.setContactPhone(dto.getContactPhone());

        // 【在保快照】报修时点固化：不在保不拒单（按次收费是收入来源），只是记下计费口径
        Contract covering = coverageService.findCoveringContract(dto.getEquipmentId());
        ticket.setCovered(covering != null);
        ticket.setContractId(covering == null ? null : covering.getId());

        ticketMapper.insert(ticket);
        writeLog(ticket.getId(), null, TicketStatus.PENDING_ASSIGN, "create",
                dto.getTitle() + (covering == null ? "（不在保·按次收费）" : "（在保）"), operator);
        return ticket;
    }

    // ══════════════ 派单/改派 ══════════════

    @Transactional
    public Ticket assign(Long ticketId, TicketAssignDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireEngineerRole(dto.getEngineerId());
        requireCurrentStatus(ticket, TicketStatus.PENDING_ASSIGN);
        changeStatus(ticket, TicketStatus.ASSIGNED, "assign",
                "派给工程师: " + nickOf(dto.getEngineerId()));
        ticket.setAssignedEngineerId(dto.getEngineerId());
        ticket.setAssignedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    @Transactional
    public Ticket reassign(Long ticketId, TicketAssignDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireEngineerRole(dto.getEngineerId());
        changeStatus(ticket, TicketStatus.ASSIGNED, "reassign",
                "改派给工程师: " + nickOf(dto.getEngineerId())
                        + (dto.getRemark() == null ? "" : "；原因: " + dto.getRemark()));
        ticket.setAssignedEngineerId(dto.getEngineerId());
        ticket.setAssignedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════ 工程师动作 ══════════════

    @Transactional
    public Ticket accept(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireAssignedEngineer(ticket);
        changeStatus(ticket, TicketStatus.IN_PROGRESS, "accept", dto.getRemark());
        ticket.setStartedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    /** 换件：免费与否按"工单快照合同的免费清单"判定，判定结果连同单价快照记进流水 */
    @Transactional
    public SparePartUsage usePart(Long ticketId, UsePartDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireAssignedEngineer(ticket);
        requireCurrentStatus(ticket, TicketStatus.IN_PROGRESS);

        // 颗粒化计费判定：在保工单 + 备件在合同免费清单 → 免费；其余计费
        boolean free = Boolean.TRUE.equals(ticket.getCovered())
                && coverageService.isPartFree(ticket.getContractId(), dto.getPartId());
        SparePartUsage usage = inventoryService.useOnTicket(dto.getPartId(), dto.getQty(),
                UserContext.current().getId(), ticket.getId(), ticket.getEquipmentId(), !free);

        writeLog(ticket.getId(), TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS, "use_part",
                "领用备件 id=" + dto.getPartId() + " x" + dto.getQty() + (free ? "（合同内免费）" : "（计费）"),
                UserContext.current());
        return usage;
    }

    /**
     * 完工：状态推进 + 自动生成结算单。
     * 不在保 → 上门费 + 维修费（工程师报价或默认标准）+ 计费配件；
     * 在保   → 只结算"不在免费清单里"的配件（有则生成 PART 明细，无则零结算）。
     * 驳回返工再完工时先清旧结算再重算（以最终那次完工为准）。
     */
    @Transactional
    public Ticket complete(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireAssignedEngineer(ticket);
        changeStatus(ticket, TicketStatus.PENDING_CONFIRM, "complete",
                dto.getRemark() == null ? "处理完成" : dto.getRemark());
        ticket.setCompletedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        generateCharges(ticket, dto.getLaborFee());
        return ticket;
    }

    private void generateCharges(Ticket ticket, BigDecimal laborFee) {
        // 返工重结：删掉上次完工生成的结算（物理删——结算单在确认前只是草案）
        chargeMapper.delete(new LambdaQueryWrapper<TicketCharge>()
                .eq(TicketCharge::getTicketId, ticket.getId()));

        boolean covered = Boolean.TRUE.equals(ticket.getCovered());
        BigDecimal total = BigDecimal.ZERO;

        // 计费配件（在保工单也可能有：不在免费清单里的件）
        List<SparePartUsage> billables = usageMapper.selectList(new LambdaQueryWrapper<SparePartUsage>()
                .eq(SparePartUsage::getTicketId, ticket.getId())
                .eq(SparePartUsage::getBillable, true));
        for (SparePartUsage u : billables) {
            SparePart part = sparePartMapper.selectById(u.getPartId());
            BigDecimal amount = u.getUnitPrice().multiply(BigDecimal.valueOf(u.getQty()));
            insertCharge(ticket.getId(), "PART",
                    (part == null ? "备件#" + u.getPartId() : part.getName()) + " ×" + u.getQty(), amount);
            total = total.add(amount);
        }
        // 上门费 + 维修费：只有不在保的单收
        if (!covered) {
            // 收费标准从平台参数表读（平台端可改即刻生效），yml 不再写死
            BigDecimal visit = paramService.getDecimal("charge.visit_fee", "200");
            insertCharge(ticket.getId(), "VISIT", "上门费", visit);
            BigDecimal labor = laborFee != null ? laborFee
                    : paramService.getDecimal("charge.labor_fee", "300");
            insertCharge(ticket.getId(), "LABOR", "维修费", labor);
            total = total.add(visit).add(labor);
        }
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            writeLog(ticket.getId(), TicketStatus.PENDING_CONFIRM, TicketStatus.PENDING_CONFIRM,
                    "charge", "生成结算单，合计 ¥" + total, UserContext.current());
        }
    }

    private void insertCharge(Long ticketId, String type, String name, BigDecimal amount) {
        TicketCharge charge = new TicketCharge();
        charge.setTicketId(ticketId);
        charge.setItemType(type);
        charge.setItemName(name);
        charge.setAmount(amount);
        chargeMapper.insert(charge);
    }

    // ══════════════ 客户动作 ══════════════

    @Transactional
    public Ticket confirm(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireTicketOwner(ticket);
        changeStatus(ticket, TicketStatus.COMPLETED, "confirm", dto.getRemark());
        LocalDateTime now = LocalDateTime.now();
        ticket.setConfirmedAt(now);
        ticket.setClosedAt(now);
        ticketMapper.updateById(ticket);
        return ticket;
    }

    @Transactional
    public Ticket reject(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        requireTicketOwner(ticket);
        changeStatus(ticket, TicketStatus.IN_PROGRESS, "reject",
                dto.getRemark() == null ? "客户驳回" : "驳回原因: " + dto.getRemark());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    @Transactional
    public Ticket cancel(Long ticketId, TicketActionDTO dto) {
        Ticket ticket = requireTicket(ticketId);
        SysUser operator = UserContext.current();
        if (operator.getRole() == UserRole.CUSTOMER) {
            requireTicketOwner(ticket);
            if (ticket.getStatus() != TicketStatus.PENDING_ASSIGN) {
                throw new BusinessException("已派单的工单客户不能自行取消，请联系管理员");
            }
        }
        changeStatus(ticket, TicketStatus.CANCELLED, "cancel",
                dto.getRemark() == null ? "取消" : "取消原因: " + dto.getRemark());
        ticket.setClosedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return ticket;
    }

    // ══════════════ 私有工具 ══════════════

    /** 状态跳转唯一入口：先问状态机，走不通抛异常（状态机是工单的法律） */
    private void changeStatus(Ticket ticket, TicketStatus target, String action, String remark) {
        TicketStatus from = ticket.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new BusinessException("非法状态跳转: " + from + " → " + target + "（动作: " + action + "）");
        }
        ticket.setStatus(target);
        writeLog(ticket.getId(), from, target, action, remark, UserContext.current());
    }

    private void writeLog(Long ticketId, TicketStatus from, TicketStatus to,
                          String action, String remark, SysUser operator) {
        TicketLog log = new TicketLog();
        log.setTicketId(ticketId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setAction(action);
        log.setOperatorId(operator.getId());
        log.setOperatorRole(operator.getRole());
        log.setOperatorName(operator.getRealName()); // 冗余昵称：历史永远可读
        log.setRemark(remark);
        logMapper.insert(log);
    }

    /** 工单号 FX+日期+3位序号；并发撞号由 UNIQUE 兜底，上量后换 Redis INCR 发号 */
    private String generateTicketNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long todayCount = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .likeRight(Ticket::getTicketNo, "FX" + datePart));
        return String.format("FX%s%03d", datePart, todayCount + 1);
    }

    private Ticket requireTicket(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException("工单不存在: id=" + id);
        }
        return ticket;
    }

    private Long requireCustomerId(SysUser user) {
        if (user.getCustomerId() == null) {
            throw new BusinessException("客户账号未关联客户单位，请联系管理员配置");
        }
        return user.getCustomerId();
    }

    private String nickOf(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? "用户#" + userId : user.getRealName();
    }

    /** 派单目标必须是工程师角色（防止把单派给客户/管理员） */
    private void requireEngineerRole(Long engineerId) {
        SysUser engineer = sysUserMapper.selectById(engineerId);
        if (engineer == null || engineer.getRole() != UserRole.ENGINEER) {
            throw new BusinessException("派单目标必须是工程师");
        }
    }

    private void requireAssignedEngineer(Ticket ticket) {
        SysUser operator = UserContext.current();
        if (operator.getRole() != UserRole.ENGINEER
                || !operator.getId().equals(ticket.getAssignedEngineerId())) {
            throw new BusinessException("无权操作: 该工单的责任工程师不是你");
        }
    }

    private void requireTicketOwner(Ticket ticket) {
        SysUser operator = UserContext.current();
        if (operator.getRole() != UserRole.CUSTOMER
                || !ticket.getCustomerId().equals(requireCustomerId(operator))) {
            throw new BusinessException("无权操作: 该工单不属于你的单位");
        }
    }

    private void requireCurrentStatus(Ticket ticket, TicketStatus expected) {
        if (ticket.getStatus() != expected) {
            throw new BusinessException("当前状态为 " + ticket.getStatus() + "，该操作要求状态为 " + expected);
        }
    }

    /** 可见性：管理员全量；工程师=派给自己的；客户=自己单位的 */
    private void requireVisible(Ticket ticket, SysUser viewer) {
        boolean visible = switch (viewer.getRole()) {
            case SUPER_ADMIN, ADMIN -> true; // 平台超管/运营管理员全可见
            case ENGINEER -> viewer.getId().equals(ticket.getAssignedEngineerId());
            case CUSTOMER -> ticket.getCustomerId().equals(requireCustomerId(viewer));
        };
        if (!visible) {
            throw new BusinessException("无权查看: 该工单与你无关");
        }
    }
}
