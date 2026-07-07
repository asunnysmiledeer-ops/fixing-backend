package com.fixing.order.controller;

import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.order.domain.SalesOrder;
import com.fixing.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售订单接口。权限分工（用户定案）：
 * 录入 = 超管（maint:order:edit 只种在 SUPER_ADMIN）；
 * 查看/派发 = 管理员（maint:order:list / maint:order:dispatch）。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @RequirePerm("maint:order:list")
    @GetMapping
    public Result<List<SalesOrder>> list(@RequestParam(required = false) String status) {
        return Result.ok(orderService.list(status));
    }

    /** 超管录单 */
    @RequirePerm("maint:order:edit")
    @PostMapping
    public Result<SalesOrder> create(@RequestBody SalesOrder order) {
        return Result.ok(orderService.create(order));
    }

    /** 管理员派发整机：body = {serialNos: [...], location} */
    @RequirePerm("maint:order:dispatch")
    @PostMapping("/{id}/dispatch-machine")
    @SuppressWarnings("unchecked")
    public Result<SalesOrder> dispatchMachine(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(orderService.dispatchMachine(id,
                (List<String>) body.get("serialNos"),
                body.get("location") == null ? null : body.get("location").toString()));
    }

    /** 管理员派发软件：body = {equipmentId?} */
    @RequirePerm("maint:order:dispatch")
    @PostMapping("/{id}/dispatch-software")
    public Result<SalesOrder> dispatchSoftware(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Object eid = body == null ? null : body.get("equipmentId");
        return Result.ok(orderService.dispatchSoftware(id, eid == null ? null : Long.valueOf(eid.toString())));
    }
}
