package com.fixing.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 维保合同实体（M3 最小版），对应表 contract。
 * 管理端专用：生命周期 + 计费方式 + 到期提醒（30 天内标"即将到期"）。
 */
@Data
@TableName("contract")
public class Contract {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签约客户（外键 → customer.id） */
    private Long customerId;

    /** 合同名，如 "2026年度叫号系统维保合同" */
    private String name;

    /** 服务范围描述 */
    private String scope;

    /** 起止日期：到期提醒按 endDate 算 */
    private LocalDate startDate;
    private LocalDate endDate;

    /** 计费方式：YEARLY 年费 / PER_CASE 按次 / MIXED 混合（需求 §3：年费+按次） */
    private String billingType;

    /** 合同金额（年费制填年费；按次制可为 0，按工单结算） */
    private BigDecimal amount;

    /** ACTIVE 生效中 / TERMINATED 已终止 */
    private String status;

    private LocalDateTime createdAt;
}
