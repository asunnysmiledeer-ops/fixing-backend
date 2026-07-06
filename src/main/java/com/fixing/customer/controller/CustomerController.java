package com.fixing.customer.controller;

import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.customer.domain.Customer;
import com.fixing.customer.mapper.CustomerMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户台账接口（M2 的客户部分）。
 * v0 只做最小 CRUD —— 报修时要选客户，所以先能建、能查即可。
 */
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerMapper customerMapper;

    public CustomerController(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    /** 新建客户 */
    @PostMapping
    public Result<Customer> create(@Valid @RequestBody Customer customer) {
        customerMapper.insert(customer); // insert 后 MyBatis-Plus 会把自增 id 回填到对象上
        return Result.ok(customer);
    }

    /** 客户列表 */
    @GetMapping
    public Result<List<Customer>> list() {
        return Result.ok(customerMapper.selectList(null));
    }

    /** 客户详情 */
    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在: id=" + id);
        }
        return Result.ok(customer);
    }
}
