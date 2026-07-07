package com.fixing.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.service.ContractService;
import com.fixing.inventory.service.InventoryService;
import com.fixing.invoice.domain.Invoice;
import com.fixing.invoice.mapper.InvoiceMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketCharge;
import com.fixing.ticket.mapper.TicketChargeMapper;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端数据看板：聚合全平台指标，Redis 缓存 60 秒。
 *
 * <p>缓存策略：看板是"多读少变、允许一分钟延迟"的典型场景 ——
 * 先查 Redis，命中直接回；未命中算一遍并写回（TTL 60s）。
 * 内存聚合在 Demo 数据量下够用；上量后换 SQL GROUP BY，接口不变。
 */
@RestController
public class DashboardController {

    private static final String CACHE_KEY = "fixing:dashboard:summary";

    private final TicketMapper ticketMapper;
    private final InventoryService inventoryService;
    private final ContractMapper contractMapper;
    private final ContractService contractService;
    private final InvoiceMapper invoiceMapper;
    private final TicketChargeMapper chargeMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DashboardController(TicketMapper ticketMapper, InventoryService inventoryService,
                               ContractMapper contractMapper, ContractService contractService,
                               InvoiceMapper invoiceMapper,
                               TicketChargeMapper chargeMapper, SysUserMapper sysUserMapper,
                               StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.ticketMapper = ticketMapper;
        this.inventoryService = inventoryService;
        this.contractMapper = contractMapper;
        this.contractService = contractService;
        this.invoiceMapper = invoiceMapper;
        this.chargeMapper = chargeMapper;
        this.sysUserMapper = sysUserMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @RequirePerm("maint:dashboard:view")
    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> summary() throws Exception {
        // 1) 先查缓存
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return Result.ok(objectMapper.readValue(cached, new TypeReference<>() {}));
        }
        // 2) 未命中：现算
        Map<String, Object> out = new LinkedHashMap<>();
        List<Ticket> tickets = ticketMapper.selectList(null);
        out.put("ticketByStatus", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getStatus().name(), LinkedHashMap::new, Collectors.counting())));
        out.put("ticketByPriority", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getPriority().name(), TreeMap::new, Collectors.counting())));
        out.put("ticketByType", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getType().name(), LinkedHashMap::new, Collectors.counting())));

        // 工程师工作量（带姓名，前端直接渲染）
        Set<String> open = Set.of("ASSIGNED", "IN_PROGRESS", "PENDING_CONFIRM");
        Map<Long, Long> openByEngineer = tickets.stream()
                .filter(t -> t.getAssignedEngineerId() != null && open.contains(t.getStatus().name()))
                .collect(Collectors.groupingBy(Ticket::getAssignedEngineerId, Collectors.counting()));
        List<Map<String, Object>> workload = new ArrayList<>();
        for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, UserRole.ENGINEER))) {
            workload.add(Map.of("engineerId", u.getId(), "realName", u.getRealName(),
                    "openCount", openByEngineer.getOrDefault(u.getId(), 0L)));
        }
        out.put("engineerWorkload", workload);

        out.put("lowStockParts", inventoryService.lowStock());

        LocalDate today = LocalDate.now();
        out.put("expiringContracts", contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "ACTIVE")
                .ge(Contract::getEndDate, today)
                .le(Contract::getEndDate, today.plusDays(contractService.remindDays()))));

        List<Invoice> unpaid = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, "ISSUED"));
        out.put("unpaidInvoiceCount", unpaid.size());
        out.put("unpaidInvoiceAmount", unpaid.stream()
                .map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        // 按次收费的应收总额（结算单合计，提醒管理员该开票了）
        out.put("chargeTotal", chargeMapper.selectList(null).stream()
                .map(TicketCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        // 3) 写回缓存
        redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(out), Duration.ofSeconds(60));
        return Result.ok(out);
    }
}
