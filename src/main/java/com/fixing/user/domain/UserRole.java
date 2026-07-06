package com.fixing.user.domain;

/**
 * 用户角色。v0 极简：一个角色字段就够（不做完整 RBAC 菜单权限）。
 *
 * <p>状态机的每个动作都规定了"谁能做"（见模块详设-M4），
 * Service 层用这个枚举做权限校验 —— 权限校验在后端，绝不信前端。
 */
public enum UserRole {

    /** 客户：报修、确认/驳回、取消（仅待派单时） */
    CUSTOMER,

    /** 管理员：派单、改派、取消 */
    ADMIN,

    /** 工程师：接单、换件、完工 */
    ENGINEER
}
