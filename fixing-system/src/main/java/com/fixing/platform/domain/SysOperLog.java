package com.fixing.platform.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（业务追踪）：谁在什么时候调了什么写接口、成功与否、耗时。
 * 由 OperLogInterceptor 自动记录全部非 GET 请求 —— 业务代码零侵入。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String userName;

    /** POST/PUT/DELETE */
    private String method;

    /** 请求路径，如 /tickets/3/assign */
    private String uri;

    /** HTTP 状态码 */
    private Integer status;

    /** 耗时毫秒 */
    private Long costMs;

    private LocalDateTime createTime;
}
