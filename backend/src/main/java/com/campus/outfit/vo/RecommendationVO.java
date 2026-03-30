package com.campus.outfit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 推荐标题
     */
    private String title;

    /**
     * 搭配描述
     */
    private String desc;

    /**
     * 生成的融合效果图 URL
     */
    private String image;
}
