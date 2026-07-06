package com.fixing.ticket.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fixing.ticket.enums.Priority;
import com.fixing.ticket.enums.TicketStatus;
import com.fixing.ticket.enums.TicketType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单实体，对应表 ticket —— 一次报修的全过程载体（核心实体）。
 *
 * <p>各节点时间戳（assignedAt/startedAt/…）分开存，不是冗余：
 * 将来算 SLA（响应时长、维修时长）全靠这些点位相减。
 */
@Data
// autoResultMap=true：让下面 photos 字段的 JSON TypeHandler 在查询时也生效
// （只标 @TableField 的话插入没问题，查出来却是 null —— MyBatis-Plus 的经典坑）
@TableName(value = "ticket", autoResultMap = true)
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务工单号，如 FX20260706001。给人看/报障沟通用，数据库层面加了 UNIQUE 约束 */
    private String ticketNo;

    /** 报修客户（外键 → customer.id），必填 */
    private Long customerId;

    /** 故障设备（外键 → equipment.id）。硬件工单必填，软件工单可空 */
    private Long equipmentId;

    /** 工单类型：HARDWARE / SOFTWARE */
    private TicketType type;

    /** 标题，必填 */
    private String title;

    /** 故障描述（PriorityDecider 的规则会读它判优先级） */
    private String description;

    /**
     * 故障图片/视频 URL 数组（M12）：客户报修必须至少传一个。
     * 数据库列是 JSON 类型，JacksonTypeHandler 负责 List<String> ↔ JSON 字符串互转。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> photos;

    /** 优先级 P0–P4，由 PriorityDecider 在建单时判定 */
    private Priority priority;

    /** 当前状态，见 TicketStatus 状态机 */
    private TicketStatus status;

    /** 责任工程师（外键 → sys_user.id），派单后才有值 */
    private Long assignedEngineerId;

    /** 报修联系人姓名/电话（现场沟通用，跟客户表的联系人可能不是同一人） */
    private String contactName;
    private String contactPhone;

    // ── 各节点时间戳：状态每前进一步，Service 负责盖一个时间戳 ──
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime closedAt;
}
