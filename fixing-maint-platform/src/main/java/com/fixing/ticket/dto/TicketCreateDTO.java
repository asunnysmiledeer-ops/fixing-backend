package com.fixing.ticket.dto;

import com.fixing.ticket.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 提交报修/服务申请的请求体（五类工单统一入口）。
 * 没有 operatorId —— 操作人由登录态认定；客户提交时 customerId 也强制取登录单位。
 */
@Data
public class TicketCreateDTO {

    /** 仅管理员代录单时需要；客户提交时忽略 */
    private Long customerId;

    /** 硬件维修/移机必填（TicketType.isEquipmentRequired） */
    private Long equipmentId;

    /** 软件维修/安装软件关联的软件实例（可选） */
    private Long softwareInstanceId;

    @NotNull(message = "工单类型不能为空")
    private TicketType type;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    /** 附件 URL（先 POST /files 上传）。维修类客户提交必须至少一个 */
    private List<String> photos;

    private String contactName;
    private String contactPhone;
}
