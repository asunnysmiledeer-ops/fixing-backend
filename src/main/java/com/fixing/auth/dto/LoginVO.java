package com.fixing.auth.dto;

import com.fixing.contract.dto.ServiceNotice;
import com.fixing.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功的响应（VO = View Object，专门给前端看的数据形状）。
 * 只挑前端需要的字段 —— 密码这类字段从结构上就不存在，杜绝"忘了脱敏"。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT 令牌，前端存起来，后续每个请求放进 Authorization 头 */
    private String token;

    private Long userId;
    private String username;
    private String realName;
    private UserRole role;

    /** 客户角色专用：所属客户单位 id（前端报修表单等不再需要选客户） */
    private Long customerId;

    /**
     * 客户角色专用：服务状态提示（登录即知）——
     * EXPIRED → 前端弹全屏"服务已到期"；EXPIRING → 顶部横幅倒计时。
     * 非客户角色为 null。
     */
    private ServiceNotice serviceNotice;
}
