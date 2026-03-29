package com.campus.outfit.dto;

import lombok.Data;

/**
 * AI 穿搭分析请求
 */
@Data
public class AiAnalyzeRequest {
    /**
     * 主图 Base64 (可选带前缀)
     */
    private String base64Image;

    /**
     * 会话 ID
     */
    private String sessionId;
}
