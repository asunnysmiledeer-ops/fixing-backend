package com.fixing.common;

/**
 * 业务异常：所有"业务规则不允许"的情况都抛它，比如：
 * 非法状态跳转、库存不足、无权操作、记录不存在。
 *
 * <p>继承 RuntimeException（非受检异常）有两个目的：
 * 1. Service 层抛出时不用层层声明 throws；
 * 2. Spring 的 @Transactional 默认只对 RuntimeException 回滚 ——
 *    业务异常抛出 = 事务回滚，正是我们想要的（如扣库存失败要整体回滚）。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
