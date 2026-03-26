package com.campus.outfit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResultVO implements Serializable {
    // 识别出的风格标签，例如：["法式", "极简", "高街", "多巴胺"]
    private List<String> styleTags;
    // 画面主体及单品抓取，例如：["阔腿裤", "风衣", "厚底鞋"]
    private List<String> itemKeywords;
    // AI基于画面针对身材比例与穿着的优化建议
    private String aestheticSuggestion;
}
