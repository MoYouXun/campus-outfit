package com.campus.outfit.service;

import com.campus.outfit.dto.AiTryOnRequest;
import com.campus.outfit.dto.AiTryOnResponse;

/**
 * AI 试衣间服务接口
 */
public interface AiTryOnService {
    /**
     * 生成 AI 试衣效果图
     * @param request 试衣请求参数
     * @param userId 当前用户ID
     * @return 试衣响应结果
     */
    AiTryOnResponse generate(AiTryOnRequest request, Long userId);
}
