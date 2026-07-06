package com.fixing.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 换件请求体（工程师在"处理中"状态用件，扣库存 + 记领料流水）。
 * 操作人从登录态取 —— 领料记到谁头上由服务端认定。
 */
@Data
public class UsePartDTO {

    @NotNull(message = "备件不能为空")
    private Long partId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer qty;
}
