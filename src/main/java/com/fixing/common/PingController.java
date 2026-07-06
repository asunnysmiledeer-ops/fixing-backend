package com.fixing.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口 —— 构建顺序 A 阶段"让工程启动"的验收点。
 * 能 curl 到 "ok" 就说明：内嵌 Tomcat 起来了、组件扫描生效了、请求映射通了。
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}
