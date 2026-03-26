package com.campus.outfit.vo;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.dto.WeatherDto;
import lombok.Data;

@Data
public class OutfitAnalyzeResponse {
    private AiAnalysisResult aiAnalysis;
    private WeatherDto weather;
}
