package com.fixing.customer.controller;

import com.fixing.auth.RequirePerm;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.customer.domain.Customer;
import com.fixing.customer.mapper.CustomerMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 客户台账接口（管理端专属，入口由 @RequirePerm 挡）。 */
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerMapper customerMapper;

    public CustomerController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @RequirePerm("maint:customer:edit")
    @PostMapping
    public Result<Customer> create(@Valid @RequestBody Customer customer) {
        customerMapper.insert(customer);
        return Result.ok(customer);
    }

    @RequirePerm("maint:customer:list")
    @GetMapping
    public Result<List<Customer>> list() {
        return Result.ok(customerMapper.selectList(null));
    }

    @RequirePerm("maint:customer:list")
    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在: id=" + id);
        }
        return Result.ok(customer);
    }
}
