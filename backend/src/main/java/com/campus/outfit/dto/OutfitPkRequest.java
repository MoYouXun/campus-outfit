package com.campus.outfit.dto;

import lombok.Data;

/**
 * 穿搭 PK 请求对象
 */
@Data
public class OutfitPkRequest {
    private String imageAUrl;
    private String imageBUrl;
    private String scene;
}
