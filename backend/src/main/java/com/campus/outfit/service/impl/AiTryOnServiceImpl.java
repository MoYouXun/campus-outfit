package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiTryOnRequest;
import com.campus.outfit.dto.AiTryOnResponse;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.AiTryOnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 试衣间服务实现类 - 逻辑已统一由 AiService 托管
 */
@Slf4j
@Service
public class AiTryOnServiceImpl implements AiTryOnService {

    @Autowired
    private AiService aiService;

    @Override
    public AiTryOnResponse generate(AiTryOnRequest request, Long userId) {
        log.info("[AI Try-On] 用户发起换装请求，用户ID: {}, 上衣: {}, 裤子: {}", 
                userId, request.getUpperGarmentUrl(), request.getLowerGarmentUrl());
        
        // 调用统一的 AI 服务接口进行图像生成，支持上下装同步处理
        // 获取服饰：目前 DressingDiffusionV2 全身模式仅支持单张服饰图，优先取上装
        String outfitUrl = request.getUpperGarmentUrl() != null ? 
                request.getUpperGarmentUrl() : request.getLowerGarmentUrl();
        
        String resultUrl = aiService.generateTryOnImage(
                request.getHumanImageUrl(), 
                outfitUrl
        );
        
        AiTryOnResponse response = new AiTryOnResponse();
        response.setResultImageUrl(resultUrl);
        
        return response;
    }
}
