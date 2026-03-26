package com.campus.outfit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherInfoVO implements Serializable {
    private String location;
    private String temperature; // 当前温度，如 "18°C"
    private String weatherDesc; // 天气描述词，如 "多云"
    private String dressIndex; // 穿衣指数描述（较冷/适宜/炎热）
    private String suggestion; // 推荐搭配建议文本
}
