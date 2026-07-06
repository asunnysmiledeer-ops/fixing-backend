package com.fixing.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.dto.LoginDTO;
import com.fixing.auth.dto.LoginVO;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.mapper.SysUserMapper;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（M1）：登录、查询当前登录人。
 *
 * <p>密码安全两原则：
 * 1. 数据库只存 BCrypt 散列，永不存明文 —— 库被拖走也拿不到原始密码；
 * 2. 登录失败只说"用户名或密码错误"，不区分是哪个错 ——
 *    否则攻击者可以先探出哪些用户名存在，再定向撞密码。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    /**
     * BCrypt：加盐慢散列。同一个密码每次散列结果都不同（盐随机），
     * 校验用 matches(明文, 散列) 而不是"再散列一次比对字符串"。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper sysUserMapper, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
    }

    /** 登录：校验通过 → 签发 JWT。此接口在拦截器白名单里（WebConfig）。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));

        // 用户不存在和密码错误给同一句提示（见类注释原则2）
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.createToken(user.getId());
        return Result.ok(new LoginVO(token, user.getId(), user.getUsername(),
                user.getRealName(), user.getRole()));
    }

    /**
     * 查询当前登录人：前端刷新页面时用它恢复登录态（token 在 localStorage 里，
     * 但用户信息要重新拿）。走到这里说明已通过拦截器，直接从 UserContext 取。
     */
    @GetMapping("/me")
    public Result<LoginVO> me() {
        SysUser user = UserContext.current();
        // token 原样不返回（前端已有）；复用 LoginVO 的形状，token 位置给 null
        return Result.ok(new LoginVO(null, user.getId(), user.getUsername(),
                user.getRealName(), user.getRole()));
    }

    /**
     * 登出。JWT 是无状态的：服务端没有会话可销毁，真正的"作废"由前端删除
     * 本地 token 完成。要做服务端强制踢人（黑名单）需要 Redis，留给上线阶段。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
