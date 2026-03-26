package com.campus.outfit.service;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.dto.AiRecommendationResult;

public interface AiService {
    /**
     * 分析穿搭图片
     * @param imageBytes 图片字节数据
     * @return 分析结果
     */
    AiAnalysisResult analyzeOutfit(byte[] imageBytes);

    /**
     * 根据场景和衣橱推荐穿搭
     * @param prompt 组装的推荐 prompt
     * @return 推荐结果
     */
    AiRecommendationResult recommendOutfit(String prompt);

    /**
     * 生成 AI 换装后的图片
     * @param personImageUrl 人像图Url
     * @param outfitImageUrl 衣服图Url
     * @return 生成的图片Base64或链接
     */
    String generateTryOnImage(String personImageUrl, String outfitImageUrl);
}
