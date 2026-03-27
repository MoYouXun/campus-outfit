package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiTryOnRequest;
import com.campus.outfit.dto.AiTryOnResponse;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.AiTryOnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 试衣间服务实现类
 */
@Slf4j
@Service
public class AiTryOnServiceImpl implements AiTryOnService {

    @Autowired
    private AiService aiService;

    @Override
    public AiTryOnResponse generate(AiTryOnRequest request, Long userId) {
        log.info("[AI Try-On] 用户选择试衣，用户ID: {}, 类别: {}", userId, request.getCategory());
        
        // 调用底层的 AiService 进行图像生成
        String resultUrl = aiService.generateTryOnImage(request.getHumanImageUrl(), request.getGarmentImageUrl());
        
        AiTryOnResponse response = new AiTryOnResponse();
        response.setResultImageUrl(resultUrl);
        
        return response;
    }
}
