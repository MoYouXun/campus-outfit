package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.LikeRecord;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.LikeMapper;
import com.campus.outfit.service.LikeService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeServiceImpl extends ServiceImpl<LikeMapper, LikeRecord> implements LikeService {

    @Autowired
    private OutfitService outfitService;

    @Override
    @Transactional
    public Result<String> like(Long userId, Long outfitId) {
        if (isLiked(userId, outfitId)) {
            return Result.fail("已点过赞");
        }
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setOutfitId(outfitId);
        save(record);

        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, outfitId)
                .setSql("like_count = like_count + 1"));
        return Result.success("点赞成功");
    }

    @Override
    @Transactional
    public Result<String> unlike(Long userId, Long outfitId) {
        boolean removed = remove(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getOutfitId, outfitId));
        if (removed) {
            outfitService.update(new LambdaUpdateWrapper<Outfit>()
                    .eq(Outfit::getId, outfitId)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
        }
        return Result.success("取消点赞");
    }

    @Override
    public boolean isLiked(Long userId, Long outfitId) {
        if (userId == null) return false;
        return count(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getOutfitId, outfitId)) > 0;
    }
}
