package com.fixing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FIX-ING Demo v0 启动类。
 *
 * <p>@SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan，
 * 组件扫描默认覆盖本类所在包(com.fixing)及其子包 —— 所以所有业务代码都放在 com.fixing 之下，
 * 否则 Bean 注入不进来（这是最常见的"自动配置失效"原因之一）。
 *
 * <p>@MapperScan：让 MyBatis 扫描所有 Mapper 接口并生成代理实现，
 * 这样每个 Mapper 接口上就不用重复写 @Mapper 注解。
 */
@SpringBootApplication
@MapperScan("com.fixing.**.mapper")
public class FixingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FixingApplication.class, args);
    }
}
