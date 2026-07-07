package com.fixing.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解（参考 zzyl 的 @PreAuthorize("@ss.hasPermi(...)") 思想，自写轻量版）。
 *
 * <p>用法：@RequirePerm("maint:ticket:assign") 标在 Controller 方法上，
 * AuthInterceptor 会校验当前登录角色是否拥有该权限字符串（映射存 sys_role_perm 表）。
 *
 * <p>权限是"字符串"而不是"角色"的意义：给角色加减权限只改数据库，不改代码不发版 ——
 * 这就是从 requireRole(ADMIN) 硬编码进化的方向。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 需要的权限字符串；填多个表示"任一满足即放行" */
    String[] value();
}
