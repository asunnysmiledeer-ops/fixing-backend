package com.fixing.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.contract.domain.Contract;
import com.fixing.contract.dto.ServiceNotice;
import com.fixing.contract.mapper.ContractMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 合同服务：除 CRUD 外，核心是"某客户的服务状态"判定 ——
 * 登录提示、报修拦截、到期前通知，三处共用同一份判定逻辑（别写三遍）。
 */
@Service
public class ContractService {

    /** 提前几天开始提醒（需求：到期前一个星期） */
    public static final int REMIND_DAYS = 7;

    private final ContractMapper contractMapper;

    public ContractService(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    /** 某客户的全部合同（管理端列表也用它，customerId 传 null 查全部） */
    public List<Contract> listByCustomer(Long customerId) {
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(customerId != null, Contract::getCustomerId, customerId)
                .orderByDesc(Contract::getEndDate));
    }

    /**
     * 判定某客户的服务状态（三种）：
     * - EXPIRED  ：没有任何"生效中且未到期"的合同 → 登录弹全屏提示、报修被拦截
     * - EXPIRING ：有效合同里最近的一份将在 7 天内到期 → 登录显示横幅提醒
     * - OK       ：服务正常
     */
    public ServiceNotice noticeFor(Long customerId) {
        LocalDate today = LocalDate.now();
        // 只看"生效中"的合同（已终止的不算服务）
        List<Contract> actives = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getCustomerId, customerId)
                .eq(Contract::getStatus, "ACTIVE"));

        // 有效 = 生效中 且 今天还没过 endDate
        List<Contract> valid = actives.stream()
                .filter(c -> !c.getEndDate().isBefore(today))
                .toList();

        if (valid.isEmpty()) {
            return new ServiceNotice("EXPIRED",
                    "您的所有维保服务已到期，报修功能已停用，请联系平台续约", null);
        }

        // 取最晚到期日（只要有一份合同还长期有效，就不算"即将断供"）
        LocalDate latestEnd = valid.stream()
                .map(Contract::getEndDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
        long daysLeft = ChronoUnit.DAYS.between(today, latestEnd);

        if (daysLeft <= REMIND_DAYS) {
            return new ServiceNotice("EXPIRING",
                    "您的维保服务将于 " + latestEnd + " 到期（剩 " + daysLeft + " 天），请及时续约",
                    (int) daysLeft);
        }
        return new ServiceNotice("OK", null, (int) daysLeft);
    }

    /** 客户当前是否还有有效服务（TicketService 报修拦截用） */
    public boolean isServiceExpired(Long customerId) {
        return "EXPIRED".equals(noticeFor(customerId).getLevel());
    }
}
