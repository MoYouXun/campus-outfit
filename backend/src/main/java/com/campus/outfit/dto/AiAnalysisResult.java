package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiAnalysisResult {
    private List<String> styleTags;
    private List<String> colorTags;
    private List<String> itemKeywords;
    private String suggestion;

    // 【修改点 2】新增季节和温度字段接收 AI 的推断结果
    private String season;
    private String temperatureRange;

    private List<String> imageUrls;
}