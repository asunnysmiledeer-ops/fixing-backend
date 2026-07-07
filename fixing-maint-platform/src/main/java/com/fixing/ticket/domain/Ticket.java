package com.fixing.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fixing.common.BaseEntity;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.enums.TicketType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单实体 —— 一次报修/服务申请的全过程载体（核心实体）。
 *
 * <p>M1 新增"计费快照"字段：covered/contractId 在**报修时点**固化
 * （合同后来到期/终止不影响已建工单的计费判定 —— 金额相关判定必须存快照，不能存引用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
// autoResultMap=true：photos 的 JSON TypeHandler 查询时才生效（只标 @TableField 会"插入正常、查出来是 null"）
@TableName(value = "ticket", autoResultMap = true)
public class Ticket extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务工单号 FX+日期+序号，UNIQUE 兜底并发撞号 */
    private String ticketNo;

    private Long customerId;

    /** 硬件维修/移机必填；添加机器/装软件可空（TicketType.isEquipmentRequired 决定） */
    private Long equipmentId;

    /** 软件维修/安装软件类工单关联的软件实例 */
    private Long softwareInstanceId;

    /** 五类：HARDWARE/SOFTWARE 维修类，INSTALL/RELOCATE/SOFTWARE_INSTALL 服务申请类 */
    private TicketType type;

    private String title;
    private String description;

    /** 故障图片/视频 URL 数组（维修类客户报修必传），JSON 列 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> photos;

    private Priority priority;
    private TicketStatus status;

    /** 责任工程师 */
    private Long assignedEngineerId;

    // ── 计费快照（报修时点固化）──

    /** 报修时设备是否在保（在保=合同内服务；不在保=按次收费） */
    private Boolean covered;

    /** 在保时命中的合同 id（换件免费清单按它查） */
    private Long contractId;

    private String contactName;
    private String contactPhone;

    // ── 各节点时间戳（SLA 计算的原料）；创建时间在 BaseEntity.createTime ──
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime closedAt;
}
