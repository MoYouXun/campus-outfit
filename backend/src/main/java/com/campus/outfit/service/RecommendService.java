package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.vo.OutfitVO;

public interface RecommendService {
    /**
     * 季节/天气推荐
     */
    IPage<OutfitVO> recommendBySeason(String city, Double latitude, Double longitude, int page, int size, Long currentUserId);

    /**
     * 场合推荐
     */
    IPage<OutfitVO> recommendByOccasion(String occasion, Double latitude, Double longitude, int page, int size, Long currentUserId);

    /**
     * 风格推荐（基于用户偏好）
     */
    IPage<OutfitVO> recommendByStyle(Long userId, int page, int size);


}
