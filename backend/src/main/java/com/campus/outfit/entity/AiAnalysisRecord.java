package com.campus.outfit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 穿搭分析记录实体类
 */
@Data
@TableName("ai_analysis_record")
public class AiAnalysisRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 分析建议风格名称
     */
    private String styleName;

    /**
     * 建议搭配的柜内单品 ID 列表 (JSON 字符串)
     */
    private String itemIds;

    /**
     * AI 生成的最终搭配效果图 (MinIO URL)
     */
    private String resultImageUrl;

    /**
     * 原始对话 JSON
     */
    private String rawResultJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
