package com.fixing.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fixing.auth.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充（配合 BaseEntity 的 @TableField(fill=...)）。
 *
 * <p>MyBatis-Plus 在每次 insert/update 前回调这里，把"谁/何时"自动写进
 * create_by/create_time/update_by/update_time —— 业务代码从此不再手动
 * setCreateTime()，也不可能忘记（审计的完整性不能依赖程序员的记性）。
 *
 * <p>定时任务等无登录上下文的场景取不到操作人 → create_by 填 null，时间照填。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", Long.class, currentUserIdOrNull());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserIdOrNull());
    }

    /** UserContext.current() 未登录会抛异常 —— 定时任务场景要容忍无操作人 */
    private Long currentUserIdOrNull() {
        try {
            return UserContext.current().getId();
        } catch (Exception e) {
            return null;
        }
    }
}
