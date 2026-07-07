package com.fixing.ticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 按次收费标准（application.yml 的 fixing.charge.* 注入）。
 * 不在保工单完工时：上门费按 visitFee，维修费默认 laborFee（工程师完工时可改报）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fixing.charge")
public class ChargeProperties {

    /** 上门费（元/次） */
    private BigDecimal visitFee = new BigDecimal("200");

    /** 维修费默认标准（元/单） */
    private BigDecimal laborFee = new BigDecimal("300");
}
