package com.fixing.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web 配置：注册认证拦截器，并声明"白名单"（不需要登录就能访问的路径）。
 *
 * <p>白名单原则：只放行"登录本身"和"静态页面资源"——
 * 页面(HTML/CSS/JS)谁都能下载没关系，页面里调的每一个数据接口都会被拦。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String uploadDir;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${fixing.upload-dir:uploads}") String uploadDir) {
        this.authInterceptor = authInterceptor;
        this.uploadDir = uploadDir;
    }

    /**
     * 把磁盘上的上传目录映射成 /files/** 可访问的静态资源。
     * 注意 file: 前缀 + 结尾的 / 缺一不可（少 / 会拼错路径）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + Path.of(uploadDir).toAbsolutePath() + "/");
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
                        // 上传的图片/视频公开可读：<img>/<video> 标签带不了 Authorization 头。
                        // 文件名是 UUID（不可枚举），Demo 可接受；上线换 OSS 私有桶 + 签名 URL
                        "/files/**",
                        "/favicon.ico",
                        "/error",                  // Spring 内置错误转发路径
                        // 接口文档（本机演示放开；对外部署时应关闭或加访问控制）
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                );
    }
}
