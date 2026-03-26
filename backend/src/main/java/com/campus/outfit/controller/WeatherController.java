package com.campus.outfit.controller;

import com.campus.outfit.service.WeatherService;
import com.campus.outfit.utils.Result;
import com.campus.outfit.vo.WeatherInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping("/now")
    public Result<WeatherInfoVO> getWeather(
            @RequestParam(required = false, defaultValue = "北京") String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        if (latitude != null && longitude != null) {
            return Result.success(weatherService.getWeatherAndDressIndexByLocation(latitude, longitude));
        } else {
            return Result.success(weatherService.getWeatherAndDressIndex(city));
        }
    }
}
