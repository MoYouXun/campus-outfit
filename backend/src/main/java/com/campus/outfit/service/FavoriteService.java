package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.utils.Result;

public interface FavoriteService extends IService<Favorite> {
    Result<String> favorite(Long userId, Long outfitId);
    Result<String> unfavorite(Long userId, Long outfitId);
    IPage<Favorite> getMyFavorites(Long userId, int page, int size);
    boolean isFavorited(Long userId, Long outfitId);
}
