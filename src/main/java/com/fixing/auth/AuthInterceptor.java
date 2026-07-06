package com.fixing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixing.common.Result;
import com.fixing.user.domain.SysUser;
import com.fixing.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：所有业务接口的第一道门。
 *
 * <p>流程：取请求头 Authorization: Bearer xxx → 验签解析出 userId →
 * 查库拿到用户 → 放进 UserContext 供本次请求使用。
 * 任何一步失败 → 直接返回 401，请求根本到不了 Controller。
 *
 * <p>为什么解析后还要查一次库：JWT 里只存 id 不存角色 ——
 * 角色以数据库当前值为准，这样"用户被改角色/被禁用"能立即生效，
 * 不用等旧 token 过期。（代价是每个请求多一次主键查询，Demo 可接受；
 * 上量后把用户缓存进 Redis。）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper; // Spring 自带的 JSON 序列化器，直接注入复用

    public AuthInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 约定格式：Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, "未登录：缺少 Authorization 请求头");
        }

        Long userId = jwtUtil.parseUserId(header.substring(7)); // 去掉 "Bearer " 前缀
        if (userId == null) {
            return reject(response, "登录已过期或令牌无效，请重新登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return reject(response, "账号不存在（可能已被删除）");
        }

        UserContext.set(user); // 后续 Controller/Service 用 UserContext.current() 取
        return true;           // 放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束必须清理 ThreadLocal（线程池复用，不清会串号）
        UserContext.clear();
    }

    /** 统一的 401 拒绝：返回和业务接口同样格式的 JSON，前端好统一处理 */
    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(message)));
        return false; // false = 拦截，不再进 Controller
    }
}
