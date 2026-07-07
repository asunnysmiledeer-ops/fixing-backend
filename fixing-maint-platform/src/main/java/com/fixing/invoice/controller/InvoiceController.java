package com.fixing.invoice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.invoice.domain.Invoice;
import com.fixing.invoice.mapper.InvoiceMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 发票接口（管理端）：开票 → 标记回款；可关联工单（按次维修的结算转开票）。 */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;

    public InvoiceController(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    @RequirePerm("maint:invoice:list")
    @GetMapping
    public Result<List<Invoice>> list(@RequestParam(required = false) Long customerId,
                                      @RequestParam(required = false) String status) {
        return Result.ok(invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(customerId != null, Invoice::getCustomerId, customerId)
                .eq(status != null, Invoice::getStatus, status)
                .orderByDesc(Invoice::getCreateTime)));
    }

    @RequirePerm("maint:invoice:edit")
    @PostMapping
    public Result<Invoice> create(@Valid @RequestBody Invoice invoice) {
        invoice.setStatus("ISSUED");
        if (invoice.getIssuedAt() == null) {
            invoice.setIssuedAt(LocalDate.now());
        }
        invoiceMapper.insert(invoice);
        return Result.ok(invoice);
    }

    @RequirePerm("maint:invoice:edit")
    @PostMapping("/{id}/mark-paid")
    public Result<Invoice> markPaid(@PathVariable Long id) {
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
