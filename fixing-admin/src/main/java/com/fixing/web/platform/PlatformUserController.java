package com.fixing.web.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.platform.service.FeatureService;
import com.fixing.user.domain.SysUser;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台·人事管理（超管专属）：员工/客户账号 CRUD、停用启用、重置密码、驻场配置。
 * "最大权限"的体现：能创建和管理包括运营管理员在内的所有账号。
 */
@RestController
@RequestMapping("/platform/users")
public class PlatformUserController {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final FeatureService featureService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PlatformUserController(SysUserMapper userMapper, FeatureService featureService) {
        this.userMapper = userMapper;
        this.featureService = featureService;
    }

    @RequirePerm("platform:user:list")
    @GetMapping
    public Result<List<SysUser>> list() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId));
        users.forEach(u -> u.setPassword(null)); // 散列也不外发
        return Result.ok(users);
    }

    /** 新增账号：默认密码 123456（BCrypt 入库），首次登录建议改密（改密功能 v 下一轮） */
    @RequirePerm("platform:user:edit")
    @PostMapping
    public Result<SysUser> create(@RequestBody SysUser input) {
        if (input.getUsername() == null || input.getUsername().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, input.getUsername())) > 0) {
            throw new BusinessException("用户名已存在: " + input.getUsername());
        }
        if (input.getRole() == UserRole.CUSTOMER && input.getCustomerId() == null) {
            throw new BusinessException("客户账号必须绑定客户单位");
        }
        SysUser user = new SysUser();
        user.setUsername(input.getUsername());
        user.setRealName(input.getRealName());
        user.setRole(input.getRole());
        user.setCustomerId(input.getRole() == UserRole.CUSTOMER ? input.getCustomerId() : null);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setStatus("0");
        userMapper.insert(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    /** 停用/启用（切换）：停用即刻生效（拦截器每请求都校验 status） */
    @RequirePerm("platform:user:edit")
    @PostMapping("/{id}/toggle-status")
    public Result<SysUser> toggleStatus(@PathVariable Long id) {
        SysUser user = require(id);
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("不能停用平台超管账号");
        }
        user.setStatus("1".equals(user.getStatus()) ? "0" : "1");
        userMapper.updateById(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    /** 重置密码为 123456 */
    @RequirePerm("platform:user:edit")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        SysUser user = require(id);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        userMapper.updateById(user);
        return Result.ok();
    }

    /**
     * 设置/取消驻场（customerId 传 null 取消）。
     * 受平台功能开关 resident_engineer 控制 —— 开关关了此入口整体失效。
     */
    @RequirePerm("platform:user:edit")
    @PostMapping("/{id}/resident")
    public Result<SysUser> setResident(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        if (!featureService.isEnabled("resident_engineer")) {
            throw new BusinessException("驻场工程师功能未启用（平台·配置里打开开关）");
        }
        SysUser user = require(id);
        if (user.getRole() != UserRole.ENGINEER) {
            throw new BusinessException("只有工程师可以设置驻场");
        }
        Object cid = body.get("customerId");
        user.setResidentCustomerId(cid == null ? null : Long.valueOf(cid.toString()));
        userMapper.updateById(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    private SysUser require(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: id=" + id);
        }
        return user;
    }
}
