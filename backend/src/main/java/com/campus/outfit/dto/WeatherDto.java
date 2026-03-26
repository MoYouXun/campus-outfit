package com.campus.outfit.dto;

import lombok.Data;

@Data
public class WeatherDto {
    private Integer temperature;
    private String dressIndex;
    private String city;
    private String weatherCondition;
}
