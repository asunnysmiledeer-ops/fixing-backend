package com.fixing.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 换件请求体（E 阶段：工程师在"处理中"状态用件，扣库存 + 记领料流水）。
 */
@Data
public class UsePartDTO {

    /** 操作人（必须是该工单的责任工程师） */
    @NotNull(message = "操作人不能为空")
    private Long operatorId;

    @NotNull(message = "备件不能为空")
    private Long partId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer qty;
}
