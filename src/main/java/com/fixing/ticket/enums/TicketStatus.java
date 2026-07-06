package com.fixing.ticket.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机 —— 本项目的灵魂（模块详设-M4 第 3 步）。
 *
 * <pre>
 * 待派单 ──派单──▶ 已派单 ──接单──▶ 处理中 ──完工──▶ 待确认 ──确认──▶ 已完成
 *   │               │ ▲                │ ▲               │
 *   │               │ └───── 改派 ─────┘ └──── 驳回 ──────┘
 *   └──取消──▶ 已取消 ◀──取消(管理员)──┘
 * </pre>
 *
 * 铁律：合法跳转全部集中定义在这里的 TRANSITIONS 表里，非法跳转一律拒绝
 * （如"已完成"不能再"接单"）。状态机就是工单的法律 —— 想改规则只改这一处。
 */
public enum TicketStatus {

    /** 待派单：客户刚提交，等管理员分配工程师 */
    PENDING_ASSIGN,

    /** 已派单：分配了工程师，等他接单 */
    ASSIGNED,

    /** 处理中：工程师已接单，正在维修（此状态下才能换件扣库存） */
    IN_PROGRESS,

    /** 待确认：工程师完工，等客户验收 */
    PENDING_CONFIRM,

    /** 已完成：客户确认修好，终态 */
    COMPLETED,

    /** 已取消：报修撤回/管理员取消，终态 */
    CANCELLED;

    /**
     * 合法跳转表：key=当前状态，value=允许去往的状态集合。
     * 用 EnumMap/EnumSet（枚举专用容器，底层是数组，查询 O(1)）。
     */
    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        // 待派单 → 派单 / 取消
        TRANSITIONS.put(PENDING_ASSIGN, EnumSet.of(ASSIGNED, CANCELLED));
        // 已派单 → 接单 / 改派(回到已派单,换人) / 取消(仅管理员)
        TRANSITIONS.put(ASSIGNED, EnumSet.of(IN_PROGRESS, ASSIGNED, CANCELLED));
        // 处理中 → 完工 / 改派(超时或换人)
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(PENDING_CONFIRM, ASSIGNED));
        // 待确认 → 确认完成 / 驳回(没修好,打回处理中)
        TRANSITIONS.put(PENDING_CONFIRM, EnumSet.of(COMPLETED, IN_PROGRESS));
        // 两个终态：哪儿也去不了
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(TicketStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(TicketStatus.class));
    }

    /** 当前状态能否跳到目标状态 —— 所有动作接口改状态前必须先问它 */
    public boolean canTransitionTo(TicketStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
