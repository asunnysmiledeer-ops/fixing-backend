package com.fixing.user.domain;

/**
 * 用户角色（M1.5 起四层）。
 * SUPER_ADMIN 是唯一"短路放行"的角色（管平台本身）；
 * ADMIN 从 M1.5 起也走权限表 —— 运营管理员的权限同样可被平台端调整。
 */
public enum UserRole {

    /** 平台超管：人事/权限/功能开关/字典/追踪，最大权限 */
    SUPER_ADMIN,

    /** 运营管理员：派单/合同/发票等业务管理 */
    ADMIN,

    /** 工程师：接单/换件/完工/申请配件 */
    ENGINEER,

    /** 客户：报修/确认/驳回 */
    CUSTOMER
}
