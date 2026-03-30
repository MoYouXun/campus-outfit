package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiAnalysisResult {
    private List<String> styleTags;
    private List<String> colorTags;
    private List<String> itemKeywords;
    private String suggestion;

    @com.fasterxml.jackson.annotation.JsonAlias({"season", "season_tag"})
    private String season;

    @com.fasterxml.jackson.annotation.JsonAlias({"temperatureRange", "temperature_range", "temperature"})
    private String temperatureRange;

    private Integer gender;

    @com.fasterxml.jackson.annotation.JsonAlias({"genderType", "gender_type", "gender"})
    private String genderType;

    private List<String> imageUrls;
}
