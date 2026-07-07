package com.fixing.contract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 客户服务状态提示：随登录响应/auth/me 一起下发，前端据此决定
 * 弹全屏"已到期"提示（EXPIRED）还是顶部横幅（EXPIRING）。
 */
@Data
@AllArgsConstructor
public class ServiceNotice {

    /** EXPIRED 已全部到期 / EXPIRING 7天内到期 / OK 正常 */
    private String level;

    /** 给用户看的提示文案（OK 时为 null） */
    private String message;

    /** 距最晚到期日剩余天数（EXPIRED 时为 null） */
    private Integer daysLeft;
}
