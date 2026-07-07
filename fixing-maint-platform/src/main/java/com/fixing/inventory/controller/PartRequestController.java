package com.fixing.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.auth.UserContext;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.inventory.domain.PartRequest;
import com.fixing.inventory.mapper.PartRequestMapper;
import com.fixing.inventory.mapper.SparePartMapper;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 配件申请接口：工程师申领（maint:part:request）→ 管理员审批（maint:part:edit）。
 * 审批通过 = 状态流转 + 原子入库，同一事务 —— 不会出现"批了但库存没加"。
 */
@RestController
@RequestMapping("/part-requests")
public class PartRequestController {

    private final PartRequestMapper requestMapper;
    private final SparePartMapper partMapper;

    public PartRequestController(PartRequestMapper requestMapper, SparePartMapper partMapper) {
        this.requestMapper = requestMapper;
        this.partMapper = partMapper;
    }

    /** 工程师提交申请 */
    @RequirePerm("maint:part:request")
    @PostMapping
    public Result<PartRequest> create(@RequestBody Map<String, Object> body) {
        PartRequest req = new PartRequest();
        req.setPartId(Long.valueOf(body.get("partId").toString()));
        req.setQty(Integer.valueOf(body.get("qty").toString()));
        if (req.getQty() <= 0) {
            throw new BusinessException("申请数量必须大于 0");
        }
        if (partMapper.selectById(req.getPartId()) == null) {
            throw new BusinessException("备件不存在: id=" + req.getPartId());
        }
        req.setTicketId(body.get("ticketId") == null ? null : Long.valueOf(body.get("ticketId").toString()));
        req.setReason(body.get("reason") == null ? null : body.get("reason").toString());
        req.setEngineerId(UserContext.current().getId()); // 申请人=登录人，不信客户端
        req.setStatus("PENDING");
        requestMapper.insert(req);
        return Result.ok(req);
    }

    /** 申请列表：工程师只看自己的（数据隔离），管理员全量可按状态筛 */
    @RequirePerm({"maint:part:request", "maint:part:edit"})
    @GetMapping
    public Result<List<PartRequest>> list(@RequestParam(required = false) String status) {
        SysUser me = UserContext.current();
        LambdaQueryWrapper<PartRequest> query = new LambdaQueryWrapper<>();
        if (me.getRole() == UserRole.ENGINEER) {
            query.eq(PartRequest::getEngineerId, me.getId());
        }
        query.eq(status != null, PartRequest::getStatus, status)
             .orderByDesc(PartRequest::getCreateTime);
        return Result.ok(requestMapper.selectList(query));
    }

    /** 批准：状态流转 + 原子入库，同一事务 */
    @RequirePerm("maint:part:edit")
    @Transactional
    @PostMapping("/{id}/approve")
    public Result<PartRequest> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        PartRequest req = requirePending(id);
        req.setStatus("APPROVED");
        req.setApproveRemark(body == null ? null : body.get("remark"));
        requestMapper.updateById(req);
        partMapper.addStock(req.getPartId(), req.getQty()); // 入库
        return Result.ok(req);
    }

    /** 驳回：只改状态，不动库存 */
    @RequirePerm("maint:part:edit")
    @PostMapping("/{id}/reject")
    public Result<PartRequest> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        PartRequest req = requirePending(id);
        req.setStatus("REJECTED");
        req.setApproveRemark(body == null ? null : body.get("remark"));
        requestMapper.updateById(req);
        return Result.ok(req);
    }

    private PartRequest requirePending(Long id) {
        PartRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在: id=" + id);
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new BusinessException("该申请已处理过（当前状态 " + req.getStatus() + "）");
        }
        return req;
    }
}
