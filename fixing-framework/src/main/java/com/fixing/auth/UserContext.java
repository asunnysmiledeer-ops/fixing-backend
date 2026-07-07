package com.fixing.auth;

import com.fixing.common.BusinessException;
import com.fixing.user.domain.SysUser;

/**
 * 当前登录用户上下文 —— 基于 ThreadLocal。
 *
 * <p>原理：Tomcat 用"一个请求一个线程"处理，AuthInterceptor 在请求进来时
 * 把解析出的用户放进当前线程的 ThreadLocal，同一请求后续的 Controller/Service
 * 随时可取，请求结束时清掉。这样业务代码不用把"操作人"层层传参。
 *
 * <p>【为什么必须 remove】Tomcat 的线程是池化复用的：请求结束线程不销毁，
 * 会去处理下一个请求。不清理的话，下一个请求可能"捡到"上一个人的身份 ——
 * 这是 ThreadLocal 最经典的内存泄漏/串号事故。
 */
public class UserContext {

    private static final ThreadLocal<SysUser> HOLDER = new ThreadLocal<>();

    /** 拦截器在鉴权通过后调用 */
    public static void set(SysUser user) {
        HOLDER.set(user);
    }

    /** 业务代码取当前操作人；拿不到说明代码路径没走鉴权，直接报错而不是返回 null */
    public static SysUser current() {
        SysUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException("未登录或登录已过期");
        }
        return user;
    }

    /** 拦截器在请求完成后调用（afterCompletion），防止线程复用串号 */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 角色断言：当前登录人必须是指定角色之一，否则抛业务异常。
     * 管理端专属接口（合同/发票/看板/台账管理）入口处调一句即可。
     */
    public static SysUser require(com.fixing.user.domain.UserRole... allowed) {
        SysUser user = current();
        for (com.fixing.user.domain.UserRole role : allowed) {
            if (user.getRole() == role) {
                return user;
            }
        }
        throw new BusinessException("无权访问：该功能仅限 " + java.util.Arrays.toString(allowed));
    }
}
