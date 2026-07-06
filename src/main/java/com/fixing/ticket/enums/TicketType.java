package com.fixing.ticket.enums;

/**
 * 工单类型：硬件上门维修 / 软件远程修复。
 * 两类工单流程共用同一个状态机，差异只在处理端（M6 vs M7）。
 */
public enum TicketType {

    /** 硬件工单：需上门、可能换件扣库存，equipmentId 必填 */
    HARDWARE,

    /** 软件工单：远程处理（v0 先共用流程，软件版本/Bug 跟踪留给 M7） */
    SOFTWARE
}
