package com.campus.outfit.service.impl;

import com.campus.outfit.service.WeatherService;
import com.campus.outfit.vo.WeatherInfoVO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate restTemplate = new RestTemplate();

    // 预定义城市坐标映射 (Open-Meteo 需要经纬度)
    private static final Map<String, double[]> CITY_COORD_MAP = new HashMap<>();
    static {
        CITY_COORD_MAP.put("北京", new double[]{39.90, 116.40});
        CITY_COORD_MAP.put("上海", new double[]{31.23, 121.47});
        CITY_COORD_MAP.put("广州", new double[]{23.13, 113.26});
        CITY_COORD_MAP.put("深圳", new double[]{22.54, 114.05});
        CITY_COORD_MAP.put("武汉", new double[]{30.59, 114.30});
        CITY_COORD_MAP.put("成都", new double[]{30.57, 104.06});
        CITY_COORD_MAP.put("杭州", new double[]{30.27, 120.15});
        CITY_COORD_MAP.put("南京", new double[]{32.06, 118.79});
        CITY_COORD_MAP.put("西安", new double[]{34.34, 108.94});
        CITY_COORD_MAP.put("长沙", new double[]{28.23, 112.93});
    }

    @Override
    public WeatherInfoVO getWeatherAndDressIndex(String city) {
        double[] coords = CITY_COORD_MAP.getOrDefault(city, CITY_COORD_MAP.get("北京"));
        return getWeatherAndDressIndexByLocation(coords[0], coords[1], city);
    }

    @Override
    public WeatherInfoVO getWeatherAndDressIndexByLocation(Double latitude, Double longitude) {
        return getWeatherAndDressIndexByLocation(latitude, longitude, "当前位置");
    }

    private WeatherInfoVO getWeatherAndDressIndexByLocation(Double lat, Double lon, String locationName) {
        try {
            // 使用 Open-Meteo 免费 API (无需 Key)
            String url = String.format("https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true", lat, lon);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("current_weather")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> current = (Map<String, Object>) response.get("current_weather");
                double temp = ((Number) current.get("temperature")).doubleValue();
                int weatherCode = ((Number) current.get("weathercode")).intValue();
                
                String desc = mapWeatherCode(weatherCode);
                String dressIndex = getDressIndex(temp);
                String suggestion = getSuggestion(temp, desc);

                return WeatherInfoVO.builder()
                        .location(locationName)
                        .temperature(Math.round(temp) + "°C")
                        .weatherDesc(desc)
                        .dressIndex(dressIndex)
                        .suggestion(suggestion)
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fallbackMock(locationName);
    }

    private String mapWeatherCode(int code) {
        if (code == 0) return "晴朗";
        if (code <= 3) return "多云";
        if (code <= 48) return "雾";
        if (code <= 67) return "细雨";
        if (code <= 77) return "雪";
        if (code <= 82) return "阵雨";
        if (code <= 99) return "雷暴";
        return "多云";
    }

    private String getDressIndex(double temp) {
        if (temp < 5) return "寒冷";
        if (temp < 15) return "偏凉";
        if (temp < 25) return "舒适";
        return "炎热";
    }

    private String getSuggestion(double temp, String desc) {
        if (temp < 5) return "天气寒冷，请务必穿上厚羽绒服或羊绒大衣，戴好围巾。";
        if (temp < 15) return "气温偏凉，建议穿夹克、风衣或毛衣，注意防风。";
        if (temp < 25) return "气候非常舒适。长袖衬衫、卫衣或薄长裙都是不错的选择。";
        return "天气炎热，建议穿着透气性好的短袖、T恤或清凉夏装。";
    }

    private WeatherInfoVO fallbackMock(String location) {
        return WeatherInfoVO.builder()
                .location(location)
                .temperature("22°C")
                .weatherDesc("多云")
                .dressIndex("舒适")
                .suggestion("温度适中，长短袖皆可。")
                .build();
    }
}
