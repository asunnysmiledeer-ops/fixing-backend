package com.fixing.invoice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.invoice.domain.Invoice;
import com.fixing.invoice.mapper.InvoiceMapper;
import com.fixing.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 发票接口（M9 应收最小版）—— 管理端专属。
 * 流程：开票（ISSUED）→ 收到打款 → 标记回款（PAID）。
 */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;

    public InvoiceController(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    /** 发票列表，可按客户/状态过滤 */
    @GetMapping
    public Result<List<Invoice>> list(@RequestParam(required = false) Long customerId,
                                      @RequestParam(required = false) String status) {
        UserContext.require(UserRole.ADMIN);
        return Result.ok(invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(customerId != null, Invoice::getCustomerId, customerId)
                .eq(status != null, Invoice::getStatus, status)
                .orderByDesc(Invoice::getCreatedAt)));
    }

    /** 开票 */
    @PostMapping
    public Result<Invoice> create(@Valid @RequestBody Invoice invoice) {
        UserContext.require(UserRole.ADMIN);
        invoice.setStatus("ISSUED");
        if (invoice.getIssuedAt() == null) {
            invoice.setIssuedAt(LocalDate.now());
        }
        invoiceMapper.insert(invoice);
        return Result.ok(invoice);
    }

    /** 标记回款 */
    @PostMapping("/{id}/mark-paid")
    public Result<Invoice> markPaid(@PathVariable Long id) {
        UserContext.require(UserRole.ADMIN);
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new BusinessException("发票不存在: id=" + id);
        }
        if ("PAID".equals(invoice.getStatus())) {
            throw new BusinessException("该发票已是回款状态");
        }
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDate.now());
        invoiceMapper.updateById(invoice);
        return Result.ok(invoice);
    }
}
