package com.fixing.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.UserContext;
import com.fixing.common.Result;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.service.ContractService;
import com.fixing.inventory.domain.SparePart;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.invoice.domain.Invoice;
import com.fixing.invoice.mapper.InvoiceMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端数据看板（M10 最小版）：一个接口聚合全平台关键指标。
 *
 * <p>v0.3 用"全查出来内存聚合"—— Demo 数据量下最简单直接；
 * 数据上量后改成 SQL GROUP BY 聚合 + Redis 缓存（M10 完整版），接口契约不变。
 */
@RestController
public class DashboardController {

    private final TicketMapper ticketMapper;
    private final SparePartMapper sparePartMapper;
    private final ContractMapper contractMapper;
    private final InvoiceMapper invoiceMapper;
    private final SysUserMapper sysUserMapper;

    public DashboardController(TicketMapper ticketMapper, SparePartMapper sparePartMapper,
                               ContractMapper contractMapper, InvoiceMapper invoiceMapper,
                               SysUserMapper sysUserMapper) {
        this.ticketMapper = ticketMapper;
        this.sparePartMapper = sparePartMapper;
        this.contractMapper = contractMapper;
        this.invoiceMapper = invoiceMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> summary() {
        UserContext.require(UserRole.ADMIN); // 全平台数据，只有管理员能看

        Map<String, Object> out = new LinkedHashMap<>();

        // ── 工单：按状态 / 按优先级分布 ──
        List<Ticket> tickets = ticketMapper.selectList(null);
        out.put("ticketByStatus", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getStatus().name(), LinkedHashMap::new, Collectors.counting())));
        out.put("ticketByPriority", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getPriority().name(), TreeMap::new, Collectors.counting())));

        // ── 工程师工作量：每人手上的"未完结"工单数（派单决策的依据） ──
        Set<String> openStatus = Set.of("ASSIGNED", "IN_PROGRESS", "PENDING_CONFIRM");
        Map<Long, Long> openByEngineer = tickets.stream()
                .filter(t -> t.getAssignedEngineerId() != null && openStatus.contains(t.getStatus().name()))
                .collect(Collectors.groupingBy(Ticket::getAssignedEngineerId, Collectors.counting()));
        List<Map<String, Object>> workload = new ArrayList<>();
        for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, UserRole.ENGINEER))) {
            workload.add(Map.of("engineerId", u.getId(), "realName", u.getRealName(),
                    "openCount", openByEngineer.getOrDefault(u.getId(), 0L)));
        }
        out.put("engineerWorkload", workload);

        // ── 库存：低于阈值的备件（补货清单） ──
        List<SparePart> lowStock = sparePartMapper.selectList(null).stream()
                .filter(p -> p.getStockQty() < p.getLowStockThreshold())
                .toList();
        out.put("lowStockParts", lowStock);

        // ── 合同：7 天内到期的（催续约清单，与客户端提醒同一时间窗） ──
        LocalDate today = LocalDate.now();
        out.put("expiringContracts", contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "ACTIVE")
                .ge(Contract::getEndDate, today)
                .le(Contract::getEndDate, today.plusDays(ContractService.REMIND_DAYS))));

        // ── 应收：未回款发票张数与总额 ──
        List<Invoice> unpaid = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, "ISSUED"));
        out.put("unpaidInvoiceCount", unpaid.size());
        out.put("unpaidInvoiceAmount", unpaid.stream()
                .map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        return Result.ok(out);
    }
}
