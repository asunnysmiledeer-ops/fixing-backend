package com.fixing.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配件申请（M1 补充）：工程师申领 → 管理员审批 → 通过自动入库。
 * 库存的"出"走领料(spare_part_usage)，"入"走这里的审批 —— 两条流水合起来就是完整台账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("part_request")
public class PartRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partId;
    private Integer qty;

    /** 申请人（工程师） */
    private Long engineerId;

    /** 关联工单（选填：为哪张单申请的） */
    private Long ticketId;

    private String reason;

    /** PENDING 待审批 / APPROVED 已批准(已入库) / REJECTED 已驳回 */
    private String status;

    /** 审批意见 */
    private String approveRemark;
}
