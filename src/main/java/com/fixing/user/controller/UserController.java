package com.fixing.user.controller;

import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口。v0 只提供一个列表查询，方便演示时查到各角色的 userId
 * （调工单动作接口时要传 operatorId）。
 *
 * <p>注意：这里为了极简直接注入 Mapper。业务复杂的模块（如 ticket）
 * 必须走 Controller → Service → Mapper 三层，逻辑收在 Service。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final SysUserMapper sysUserMapper;

    /** 构造器注入：依赖显式、字段可 final、脱离容器也能 new 出来单测 */
    public UserController(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /** 查全部用户（演示用；真实系统要分页+脱敏） */
    @GetMapping
    public Result<List<SysUser>> list() {
        List<SysUser> users = sysUserMapper.selectList(null);
        // 演示接口也不把密码吐出去 —— 养成习惯
        users.forEach(u -> u.setPassword(null));
        return Result.ok(users);
    }
}
