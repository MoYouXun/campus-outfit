package com.campus.outfit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 穿搭推荐视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOutfitRecommendVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 穿搭风格
     */
    private String style;

    /**
     * 针对性建议列表
     */
    private List<String> suggestions;

    /**
     * 核心推荐搭配单品（包含生成的融合图）
     * 【修复】：将单个对象改为 List 集合，以匹配大模型返回的 JSON 数组
     */
    private List<RecommendationVO> recommendations;
}