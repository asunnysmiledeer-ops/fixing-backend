package com.fixing.auth.dto;

import com.fixing.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 登录成功的响应（VO 只挑前端需要的字段，密码从结构上就不存在）。
 *
 * <p>M1：新增 perms 权限字符串列表 —— 前端"画哪些页签/按钮"由它驱动
 * （参考 zzyl/若依的 RBAC 思想：权限是字符串，不是写死的角色判断）。
 * 服务到期提示已解耦到业务模块的 /contracts/service-notice，认证模块保持纯粹。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT 令牌；/auth/me 时为 null（前端已有） */
    private String token;

    private Long userId;
    private String username;
    private String realName;
    private UserRole role;

    /** 客户角色专用：所属客户单位 id */
    private Long customerId;

    /** 当前角色拥有的权限字符串，如 ["maint:ticket:list", "maint:ticket:add"] */
    private List<String> perms;
}
