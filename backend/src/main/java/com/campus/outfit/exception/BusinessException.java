package com.campus.outfit.exception;

/**
 * 自定义业务异常类，用于处理穿搭模型及其他模块的业务逻辑错误。
 */
public class BusinessException extends RuntimeException {
    
    public BusinessException(String message) {
        super(message);
    }
}
