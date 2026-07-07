package com.fixing.web.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.invoice.domain.Invoice;
import com.fixing.invoice.mapper.InvoiceMapper;
import com.fixing.platform.domain.SysOperLog;
import com.fixing.platform.mapper.SysOperLogMapper;
import com.fixing.ticket.domain.Ticket;
import com.fixing.ticket.domain.TicketCharge;
import com.fixing.ticket.mapper.TicketChargeMapper;
import com.fixing.ticket.mapper.TicketMapper;
import com.fixing.customer.mapper.CustomerMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台·业务追踪：操作日志 + 经营总览（跨客户的平台级视角）。
 */
@RestController
@RequestMapping("/platform")
public class PlatformTraceController {

    private final SysOperLogMapper operLogMapper;
    private final TicketMapper ticketMapper;
    private final TicketChargeMapper chargeMapper;
    private final InvoiceMapper invoiceMapper;
    private final CustomerMapper customerMapper;
    private final SysUserMapper userMapper;

    public PlatformTraceController(SysOperLogMapper operLogMapper, TicketMapper ticketMapper,
                                   TicketChargeMapper chargeMapper, InvoiceMapper invoiceMapper,
                                   CustomerMapper customerMapper, SysUserMapper userMapper) {
        this.operLogMapper = operLogMapper;
        this.ticketMapper = ticketMapper;
        this.chargeMapper = chargeMapper;
        this.invoiceMapper = invoiceMapper;
        this.customerMapper = customerMapper;
        this.userMapper = userMapper;
    }

    /** 操作日志：最近 N 条（默认200），可按用户名过滤 */
    @RequirePerm("platform:log:list")
    @GetMapping("/oper-logs")
    public Result<List<SysOperLog>> operLogs(@RequestParam(required = false) String userName,
                                             @RequestParam(defaultValue = "200") Integer limit) {
        return Result.ok(operLogMapper.selectList(new LambdaQueryWrapper<SysOperLog>()
                .like(userName != null && !userName.isBlank(), SysOperLog::getUserName, userName)
                .orderByDesc(SysOperLog::getId)
                .last("LIMIT " + Math.min(limit, 500)))); // last 只拼常量，入参已钳制
    }

    /** 经营总览：平台级指标（客户数/人员/工单结构/收入） */
    @RequirePerm("platform:overview:view")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customerCount", customerMapper.selectCount(null));
        out.put("engineerCount", userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, UserRole.ENGINEER)));

        List<Ticket> tickets = ticketMapper.selectList(null);
        out.put("ticketTotal", tickets.size());
        out.put("ticketByType", tickets.stream().collect(Collectors.groupingBy(
                t -> t.getType().name(), LinkedHashMap::new, Collectors.counting())));
        // 工程师绩效：完成单量（后续可加平均时长）
        out.put("engineerCompleted", tickets.stream()
                .filter(t -> t.getAssignedEngineerId() != null && "COMPLETED".equals(t.getStatus().name()))
                .collect(Collectors.groupingBy(Ticket::getAssignedEngineerId, Collectors.counting())));

        // 收入：合同发票已回款 + 按次结算应收
        BigDecimal paidInvoice = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                        .eq(Invoice::getStatus, "PAID"))
                .stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chargeTotal = chargeMapper.selectList(null)
                .stream().map(TicketCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        out.put("paidInvoiceTotal", paidInvoice);
        out.put("chargeTotal", chargeTotal);
        return Result.ok(out);
    }
}
