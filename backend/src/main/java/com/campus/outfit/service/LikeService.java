package com.campus.outfit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.LikeRecord;
import com.campus.outfit.utils.Result;

public interface LikeService extends IService<LikeRecord> {
    Result<String> like(Long userId, Long outfitId);
    Result<String> unlike(Long userId, Long outfitId);
    boolean isLiked(Long userId, Long outfitId);
}
