package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiRecommendationResult {
    private List<Long> outfitIds; // 推荐的单品ID列表
    private String reasoning;     // AI 给出的穿搭建议和分析
    private List<String> searchTags; // AI 获取的补充搜索关键词
}
