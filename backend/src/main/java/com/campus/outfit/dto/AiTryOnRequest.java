package com.campus.outfit.dto;

import lombok.Data;

/**
 * AI 试衣请求对象
 */
@Data
public class AiTryOnRequest {
    private String humanImageUrl;
    private String garmentImageUrl;
    private String category = "upper_body";
}
