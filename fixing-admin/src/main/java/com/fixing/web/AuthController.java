package com.fixing.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.JwtUtil;
import com.fixing.auth.TokenBlacklist;
import com.fixing.auth.UserContext;
import com.fixing.auth.dto.LoginDTO;
import com.fixing.auth.dto.LoginVO;
import com.fixing.common.BusinessException;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.mapper.SysUserMapper;
import com.fixing.user.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（放在 admin 模块的 web 包 —— 对标 zzyl-admin 持有系统级 Controller）。
 *
 * <p>M1 变化：
 * 1. 登录响应带**权限字符串列表**（perms）—— 前端页签由权限驱动，不再按角色硬编码；
 * 2. 登出把令牌放进 Redis 黑名单 —— JWT 从"删了本地也拦不住重放"升级为**真退出**；
 * 3. 不再耦合合同服务（服务到期提示由业务模块单独提供），模块边界干净。
 *
 * <p>密码安全两原则不变：只存 BCrypt 散列；登录失败不区分用户名/密码错。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final TokenBlacklist tokenBlacklist;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(SysUserMapper sysUserMapper, JwtUtil jwtUtil,
                          PermissionService permissionService, TokenBlacklist tokenBlacklist) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.tokenBlacklist = tokenBlacklist;
    }

    /** 登录：校验通过 → 签发 JWT + 下发该角色的权限列表 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if ("1".equals(user.getStatus())) {
            throw new BusinessException("账号已停用，请联系平台管理员");
        }
        return Result.ok(buildVO(user, jwtUtil.createToken(user.getId())));
    }

    /** 当前登录人（前端刷新恢复登录态；权限每次重查，后台改权限即时生效） */
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.ok(buildVO(UserContext.current(), null));
    }

    /**
     * 登出：把当前令牌加入 Redis 黑名单（TTL=令牌剩余有效期，到期自动清理）。
     * 之后任何人再拿这个令牌来，拦截器直接 401 —— 这才是"真退出"。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenBlacklist.revoke(header.substring(7));
        }
        return Result.ok();
    }

    private LoginVO buildVO(SysUser user, String token) {
        return new LoginVO(token, user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getCustomerId(), permissionService.permsOf(user.getRole()));
    }
}
