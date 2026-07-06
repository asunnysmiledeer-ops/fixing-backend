package com.fixing.ticket.dto;

import com.fixing.ticket.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交报修的请求体（UC1）。
 *
 * <p>为什么不用 Ticket 实体直接接参？—— 实体里有 status/priority/各时间戳
 * 这类"只能由服务端决定"的字段，直接接参等于允许客户端替你定状态（越权注入）。
 * DTO 只暴露客户端"应该"给的字段，这就是 DTO 和实体分开的意义。
 */
@Data
public class TicketCreateDTO {

    // 注意：这里没有 operatorId —— 操作人一律从登录态(UserContext)取。
    // "我是谁"由服务端认定，客户端说了不算，这是认证后最重要的观念转变。

    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 硬件工单必填；软件工单可空（Service 里按 type 校验） */
    private Long equipmentId;

    @NotNull(message = "工单类型不能为空")
    private TicketType type;

    @NotBlank(message = "标题不能为空")
    private String title;

    /** 故障描述 —— 优先级规则会扫描它的关键词 */
    private String description;

    private String contactName;
    private String contactPhone;
}
