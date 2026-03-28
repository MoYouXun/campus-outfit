package com.campus.outfit.config;

import com.campus.outfit.utils.Result;
import com.campus.outfit.exception.BusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理所有异常
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        // 记录日志
        e.printStackTrace();
        // 返回错误响应
        return Result.fail(500, "系统内部错误：" + e.getMessage());
    }

    // 处理业务逻辑异常
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    // 可以在这里添加其他特定类型异常的处理方法
    // 例如：@ExceptionHandler(NullPointerException.class)
    // 例如：@ExceptionHandler(IllegalArgumentException.class)
    // 例如：@ExceptionHandler(CustomBusinessException.class)
}
