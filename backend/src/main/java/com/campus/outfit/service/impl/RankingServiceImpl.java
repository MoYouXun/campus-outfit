package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RankingServiceImpl implements RankingService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OutfitService outfitService;

    private static final String RANK_HOT_KEY = "ranking:hot";

    @Override
    public List<Outfit> getHotRanking(int limit) {
        // 尝试从 Redis 获取
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(RANK_HOT_KEY, 0, limit - 1);
        if (ids == null || ids.isEmpty()) {
            return fallbackRanking(limit);
        }
        
        List<Long> outfitIds = ids.stream().map(obj -> Long.valueOf(obj.toString())).collect(Collectors.toList());
        return outfitService.listByIds(outfitIds);
    }

    @Override
    public List<Outfit> getStyleRanking(String style, int limit) {
        // 简化版：从数据库按风格筛选热度最高的
        return outfitService.list(new LambdaQueryWrapper<Outfit>()
                .apply("JSON_CONTAINS(style_tags, '\"{0}\"')", style)
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getLikeCount)
                .last("LIMIT " + limit));
    }

    @Override
    public List<Outfit> getSchoolRanking(String school, int limit) {
        // 后续扩展：结合 User 表进行联表查询，此处暂取全库热度
        return fallbackRanking(limit);
    }

    @Override
    public void refreshRankings() {
        // 获取热度最高的 100 条
        List<Outfit> topOutfits = outfitService.list(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getLikeCount)
                .last("LIMIT 100"));
        
        redisTemplate.delete(RANK_HOT_KEY);
        for (Outfit outfit : topOutfits) {
            double score = outfit.getLikeCount() * 1.0 + outfit.getCommentCount() * 1.5;
            redisTemplate.opsForZSet().add(RANK_HOT_KEY, outfit.getId().toString(), score);
        }
    }

    private List<Outfit> fallbackRanking(int limit) {
        return outfitService.list(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getLikeCount)
                .last("LIMIT " + limit));
    }
}
