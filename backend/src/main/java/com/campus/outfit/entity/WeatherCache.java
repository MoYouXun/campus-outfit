package com.campus.outfit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("weather_cache")
public class WeatherCache implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT, value = "city_id")
    private String cityId;

    @TableField("temperature")
    private Integer temperature;

    @TableField("dress_index")
    private String dressIndex;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}