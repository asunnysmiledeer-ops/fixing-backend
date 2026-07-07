package com.fixing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.mapper.SysUserMapper;
import com.fixing.user.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证+鉴权拦截器：所有业务接口的第一道门，M1 起负责三件事——
 *
 * 1. 认证：Authorization: Bearer xxx → 查黑名单 → 验签 → 查用户 → UserContext；
 * 2. 鉴权：方法上有 @RequirePerm 时，校验当前角色是否拥有该权限字符串（无权=403）；
 * 3. 清理：请求结束清 ThreadLocal（线程池复用，不清会串号）。
 *
 * <p>权限分两层的约定不变：这里挡"角色不对的"（入口权限），
 * Service 层挡"角色对但对象不对的"（工程师B动A的单）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final TokenBlacklist tokenBlacklist;
    private final PermissionService permissionService;

    public AuthInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper, ObjectMapper objectMapper,
                           TokenBlacklist tokenBlacklist, PermissionService permissionService) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
        this.tokenBlacklist = tokenBlacklist;
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // ── 1. 认证 ──
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "未登录：缺少 Authorization 请求头");
        }
        String token = header.substring(7);

        // 先查黑名单再验签：已登出的令牌即使没过期也拒绝（真退出）
        if (tokenBlacklist.isRevoked(token)) {
            return reject(response, 401, "登录已失效（已退出），请重新登录");
        }
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            return reject(response, 401, "登录已过期或令牌无效，请重新登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return reject(response, 401, "账号不存在（可能已被删除）");
        }
        if ("1".equals(user.getStatus())) {
            return reject(response, 401, "账号已停用，请联系平台管理员"); // 停用即刻生效，不等令牌过期
        }
        UserContext.set(user);

        // ── 2. 鉴权：@RequirePerm ──
        if (handler instanceof HandlerMethod method) {
            RequirePerm perm = method.getMethodAnnotation(RequirePerm.class);
            if (perm != null && !permissionService.hasAny(user.getRole(), perm.value())) {
                return reject(response, 403, "无权访问：需要权限 " + String.join(" 或 ", perm.value()));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear(); // 线程复用防串号
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(message)));
        return false;
    }
}
