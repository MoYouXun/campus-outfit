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
        
        // 收集服饰：支持上下装同步处理，将所有非空的服饰图加入列表
        java.util.List<String> outfitUrls = new java.util.ArrayList<>();
        if (request.getUpperGarmentUrl() != null) outfitUrls.add(request.getUpperGarmentUrl());
        if (request.getLowerGarmentUrl() != null) outfitUrls.add(request.getLowerGarmentUrl());
        
        String resultUrl = aiService.generateTryOnImage(
                request.getHumanImageUrl(), 
                outfitUrls
        );
        
        AiTryOnResponse response = new AiTryOnResponse();
        response.setResultImageUrl(resultUrl);
        
        return response;
    }
}
