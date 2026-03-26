package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.FavoriteMapper;
import com.campus.outfit.service.FavoriteService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Autowired
    private OutfitService outfitService;

    @Override
    @Transactional
    public Result<String> favorite(Long userId, Long outfitId) {
        if (isFavorited(userId, outfitId)) {
            return Result.fail("已收藏过");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setOutfitId(outfitId);
        save(favorite);

        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, outfitId)
                .setSql("fav_count = fav_count + 1"));
        return Result.success("收藏成功");
    }

    @Override
    @Transactional
    public Result<String> unfavorite(Long userId, Long outfitId) {
        boolean removed = remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getOutfitId, outfitId));
        if (removed) {
            outfitService.update(new LambdaUpdateWrapper<Outfit>()
                    .eq(Outfit::getId, outfitId)
                    .setSql("fav_count = GREATEST(0, fav_count - 1)"));
        }
        return Result.success("取消收藏");
    }

    @Override
    public IPage<Favorite> getMyFavorites(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));
    }

    @Override
    public boolean isFavorited(Long userId, Long outfitId) {
        if (userId == null) return false;
        return count(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getOutfitId, outfitId)) > 0;
    }
}
