package com.fixing.ticket.enums;

/**
 * 工单类型（M1 扩展为五类）。
 *
 * <p>前两类是"维修"（有故障，客户报修必须传故障图片/视频）；
 * 后三类是"服务申请"（添加机器/移动机器/安装软件 —— 没有故障现场，不强制传图）。
 * 五类共用同一套状态机：流程稳定，类型只是入口差异。
 */
public enum TicketType {

    /** 硬件维修：上门、可换件，必须挂设备 */
    HARDWARE(true, true),

    /** 软件维修：远程处理，关联软件实例 */
    SOFTWARE(true, false),

    /** 添加机器：新装设备（完工后管理端登记进设备台账） */
    INSTALL(false, false),

    /** 移动机器：现有设备搬迁，必须指明哪台 */
    RELOCATE(false, true),

    /** 安装软件：部署/升级软件，关联软件实例 */
    SOFTWARE_INSTALL(false, false);

    /** 是否维修类（客户报修必传故障图/视频的范围） */
    private final boolean repair;

    /** 是否必须关联设备 */
    private final boolean equipmentRequired;

    TicketType(boolean repair, boolean equipmentRequired) {
        this.repair = repair;
        this.equipmentRequired = equipmentRequired;
    }

    public boolean isRepair() {
        return repair;
    }

    public boolean isEquipmentRequired() {
        return equipmentRequired;
    }
}
