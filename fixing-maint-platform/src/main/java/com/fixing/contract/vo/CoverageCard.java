package com.fixing.contract.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 在保状态卡（派单界面顶部那块）：管理员派单前一眼看清
 * "保不保、哪份合同、快不快到期、哪些备件免费"。
 */
@Data
@AllArgsConstructor
public class CoverageCard {

    /** 报修时点的在保快照（来自工单，不实时重算 —— 计费以此为准） */
    private Boolean covered;

    private Long contractId;
    private String contractName;
    private LocalDate contractEndDate;

    /** 距到期天数（不在保为 null） */
    private Long daysLeft;

    /** 合同内免费更换的备件名（换件时工程师能预判收不收费） */
    private List<String> freePartNames;

    /** 给人看的计费提示："合同内服务" / "不在保：按次收费（上门费+配件费+维修费）" */
    private String billingNote;
}
