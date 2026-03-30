package com.campus.outfit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "outfit", autoResultMap = true)
public class Outfit implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableField(exist = false)
    private Long topicId;

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    @TableField("thumbnail_url")
    private String thumbnailUrl;
    private String aiAnalysis;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> styleTags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> colorTags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> itemKeywords;

    @TableField("season")
    private String season;

    private String occasion;

    @TableField("temperature_range")
    private String temperatureRange;
    @TableField("status")
    private String status;
    private Boolean isPublic;

    private Integer likeCount;
    private Integer commentCount;
    private Integer favCount;
    private Integer viewCount;

    @TableField(exist = false)
    private Boolean liked = false;

    @TableField(exist = false)
    private Boolean favorited = false;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}