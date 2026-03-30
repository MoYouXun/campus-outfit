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
    @com.fasterxml.jackson.annotation.JsonAlias({"season", "season_tag"})
    private String season;

    @com.fasterxml.jackson.annotation.JsonAlias({"temperatureRange", "temperature_range", "temperature"})
    private String temperatureRange;

    private List<String> imageUrls;
}