package com.fixing.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：Controller 层抛出的异常统一在这里翻译成 Result 响应。
 *
 * <p>好处：Service 里只管抛 BusinessException，不用在每个 Controller
 * 写 try-catch —— 异常处理集中一处，接口代码保持干净。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：规则不允许（非法跳转/库存不足/越权…）。
     * 返回 HTTP 400 + 具体原因，调用方看 message 就知道错在哪。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /**
     * 参数校验失败：@Valid 校验不通过时 Spring 抛这个异常。
     * 取第一条校验错误返回，够用且不啰嗦。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.fail(msg);
    }

    /**
     * 静态资源/接口路径不存在：这是正常的 404，不是服务器错误。
     * 不单独处理的话会掉进下面的兜底 Exception 处理器变成 500 ——
     * 真实案例：浏览器自动请求 /favicon.ico，日志里刷出一整段误报堆栈。
     * 教训：兜底 handler 一时爽，框架自带的语义异常要在它之前接住。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        return Result.fail("资源不存在: " + e.getResourcePath());
    }

    /**
     * 兜底：没预料到的异常（NPE、SQL 报错…）。
     * 打完整堆栈到日志（排错靠它），但对外只回一句笼统的话，不泄露内部细节。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("未预期的异常", e);
        return Result.fail("服务器内部错误，请查看日志");
    }
}
