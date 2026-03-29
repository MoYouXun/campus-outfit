package com.campus.outfit.dto;

import lombok.Data;

/**
 * AI 穿搭对话请求
 */
@Data
public class AiChatRequest {
    /**
     * 对话消息内容
     */
    private String message;

    /**
     * 会话 ID
     */
    private String sessionId;
}
