package com.fixing.auth.dto;

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
}
