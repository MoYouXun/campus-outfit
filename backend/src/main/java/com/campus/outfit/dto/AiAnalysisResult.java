package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiAnalysisResult {
    private List<String> styleTags;      // 风格标签
    private List<String> colorTags;      // 颜色标签
    private List<String> itemKeywords;   // 单品关键词
    private String proportionSuggestion; // 穿搭/比例建议
    private List<String> imageUrls;      // 图片在 MinIO 中的访问地址
}
