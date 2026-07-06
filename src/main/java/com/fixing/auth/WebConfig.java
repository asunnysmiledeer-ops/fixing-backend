package com.fixing.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册认证拦截器，并声明"白名单"（不需要登录就能访问的路径）。
 *
 * <p>白名单原则：只放行"登录本身"和"静态页面资源"——
 * 页面(HTML/CSS/JS)谁都能下载没关系，页面里调的每一个数据接口都会被拦。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")            // 默认全拦
                .excludePathPatterns(
                        "/auth/login",             // 登录接口本身（不然永远登不进来）
                        "/ping",                   // 健康检查
                        "/", "/index.html", "/login.html",  // 页面
                        "/css/**", "/js/**",       // 静态资源
                        "/favicon.ico",
                        "/error"                   // Spring 内置错误转发路径
                );
    }
}
