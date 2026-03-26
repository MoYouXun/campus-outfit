package com.campus.outfit.service;

import com.campus.outfit.vo.WeatherInfoVO;

public interface WeatherService {

    /**
     * 根据城市名称获取穿衣指数及天气预报
     * 
     * @param locationCity 城市名称，如 "北京"
     * @return 包含温度及穿衣红黑榜建议的数据透视对象
     */
    WeatherInfoVO getWeatherAndDressIndex(String locationCity);
    
    /**
     * 根据经纬度获取穿衣指数及天气预报
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @return 包含温度及穿衣红黑榜建议的数据透视对象
     */
    WeatherInfoVO getWeatherAndDressIndexByLocation(Double latitude, Double longitude);
}
