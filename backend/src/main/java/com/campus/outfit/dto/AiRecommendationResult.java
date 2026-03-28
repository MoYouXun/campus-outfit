package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiRecommendationResult {
    private List<Long> outfitIds;     // 推荐的单品ID列表
    private List<String> recommendedItems; // 推荐的单品文件标识
    private String reasoning;         // AI 给出的穿搭建议和分析
    private String styleType;         // AI 判断的风格名称
    private String occasion;          // AI 推荐场景
    private String imageUrl;          // Seedream 生成的融合图 URL
    private List<String> searchTags;  // AI 获取的补充搜索关键词
}
