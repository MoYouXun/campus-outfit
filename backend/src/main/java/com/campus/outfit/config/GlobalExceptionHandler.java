package com.campus.outfit.config;

import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 处理系统内部异常
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        // 使用 log.error 记录异常堆栈
        log.error("系统内部异常: ", e);
        // 返回错误响应
        return Result.fail(500, "系统内部错误：" + e.getMessage());
    }
}
