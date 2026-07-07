package com.fixing.framework.web;

import com.fixing.auth.UserContext;
import com.fixing.platform.domain.SysOperLog;
import com.fixing.platform.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 操作日志拦截器（业务追踪）：自动记录全部**非 GET** 请求 ——
 * 谁 / 何时 / 调了什么 / 成功与否 / 耗时。业务代码零侵入。
 *
 * <p>注册在 AuthInterceptor 之后（要用它放进 UserContext 的登录人）。
 * 记录失败绝不影响业务请求（日志是旁路，不能反噬主流程）。
 */
@Component
public class OperLogInterceptor implements HandlerInterceptor {

    private static final String START_AT = "operLogStartAt";

    private final SysOperLogMapper operLogMapper;

    public OperLogInterceptor(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_AT, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return; // 只追踪"改变了什么"的操作，读操作不记（量大且无审计价值）
        }
        try {
            SysOperLog log = new SysOperLog();
            try {
                var user = UserContext.current();
                log.setUserId(user.getId());
                log.setUserName(user.getRealName());
            } catch (Exception e) {
                log.setUserName("(未登录)"); // 登录接口本身等
            }
            log.setMethod(request.getMethod());
            log.setUri(request.getRequestURI());
            log.setStatus(response.getStatus());
            Object start = request.getAttribute(START_AT);
            log.setCostMs(start == null ? null : System.currentTimeMillis() - (Long) start);
            log.setCreateTime(LocalDateTime.now());
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 旁路日志失败不影响业务
        }
    }
}
