package com.fixing.ticket.enums;

/**
 * 工单优先级 P0–P4（需求梳理 v0.2 §6）。
 * 由 PriorityDecider 接口判定 —— v0 是关键词规则，将来换 AI 实现，业务代码不动。
 */
public enum Priority {

    /** P0：全院叫号停摆（最高优先级） */
    P0,

    /** P1：全科室停摆 */
    P1,

    /** P2：多台设备/部分功能受影响 */
    P2,

    /** P3：单台故障（默认级别） */
    P3,

    /** P4：一般咨询、非紧急 */
    P4
}
