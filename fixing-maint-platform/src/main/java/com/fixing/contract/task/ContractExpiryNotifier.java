package com.fixing.contract.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.mapper.ContractMapper;
import com.fixing.contract.service.ContractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 合同到期通知定时任务（M11 通知先用 mock：打日志代替真实短信/微信）。
 *
 * <p>需求：到期前一个星期开始提醒客户。
 * 除了这里的"主动推"，客户每次登录时 /auth/me 也会带上服务状态（"被动拉"），
 * 两条路共用 ContractService 的同一份判定逻辑。
 *
 * <p>@Scheduled(cron)：六位 cron = 秒 分 时 日 月 周。每天 09:00 跑一次。
 * 需要启动类加 @EnableScheduling 才会生效。
 */
@Slf4j
@Component
public class ContractExpiryNotifier {

    private final ContractMapper contractMapper;
    private final ContractService contractService;

    public ContractExpiryNotifier(ContractMapper contractMapper, ContractService contractService) {
        this.contractMapper = contractMapper;
        this.contractService = contractService;
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void notifyExpiring() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(contractService.remindDays());

        // 生效中、且 endDate 落在 [今天, 今天+7天] 区间的合同
        List<Contract> expiring = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "ACTIVE")
                .ge(Contract::getEndDate, today)
                .le(Contract::getEndDate, deadline));

        for (Contract c : expiring) {
            // 真实场景：调微信订阅消息/短信服务商。Demo 阶段 = 打日志（阶段定位文档的约定）
            log.warn("[通知mock][合同到期提醒] 客户#{} 的合同「{}」将于 {} 到期，请及时联系续约",
                    c.getCustomerId(), c.getName(), c.getEndDate());
        }
        if (expiring.isEmpty()) {
            log.info("[合同到期检查] 今日无 {} 天内到期的合同", contractService.remindDays());
        }
    }
}
