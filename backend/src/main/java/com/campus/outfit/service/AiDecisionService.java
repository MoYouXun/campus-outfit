package com.campus.outfit.service;

import com.campus.outfit.dto.OutfitPkRequest;
import com.campus.outfit.dto.OutfitPkResponse;

/**
 * AI 决策推断服务接口
 */
public interface AiDecisionService {
    /**
     * 穿搭 A/B PK
     * @param request PK 请求内容（图片URL、场景）
     * @param userId 用户ID
     * @return PK 结果
     */
    OutfitPkResponse pkOutfits(OutfitPkRequest request, Long userId);
}
