package com.fixing.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应包装：所有接口都返回同一个壳子 { code, message, data }。
 *
 * <p>约定：code=0 成功；code=1 业务失败（如库存不足、非法状态跳转）；
 * 前端/调用方只看 code 就知道成败，不用去猜 HTTP 状态码。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 0=成功，1=业务失败 */
    private Integer code;

    /** 给人看的提示信息 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    /** 成功且带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    /** 成功但没有数据要返回（如纯动作类接口） */
    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    /** 业务失败，带原因 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(1, message, null);
    }
}
