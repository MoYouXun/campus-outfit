package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.User;
import com.campus.outfit.mapper.UserMapper;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RankingService;
import com.campus.outfit.vo.OutfitVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingServiceImpl implements RankingService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private UserMapper userMapper;

    private static final String RANK_HOT_KEY = "ranking:hot";

    @Override
    public List<OutfitVO> getHotRanking(int limit) {
        // 尝试从 Redis 获取
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(RANK_HOT_KEY, 0, limit - 1);
        if (ids == null || ids.isEmpty()) {
            return fallbackRanking(limit);
        }
        
        List<Long> outfitIds = ids.stream().map(obj -> Long.valueOf(obj.toString())).collect(Collectors.toList());
        List<Outfit> outfits = outfitService.listByIds(outfitIds);
        return convertToVOList(outfits);
    }

    @Override
    public List<OutfitVO> getStyleRanking(String style, int limit) {
        // 简化版：从数据库按风格筛选热度最高的
        List<Outfit> outfits = outfitService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Outfit>()
                .apply("JSON_CONTAINS(style_tags, '\"{0}\"')", style)
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getLikeCount)
                .last("LIMIT " + limit));
        return convertToVOList(outfits);
    }

    @Override
    public List<OutfitVO> getSchoolRanking(String school, int limit) {
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

    private List<OutfitVO> fallbackRanking(int limit) {
        List<Outfit> outfits = outfitService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .orderByDesc(Outfit::getLikeCount)
                .last("LIMIT " + limit));
        return convertToVOList(outfits);
    }

    private List<OutfitVO> convertToVOList(List<Outfit> outfits) {
        if (outfits == null || outfits.isEmpty()) {
            return new ArrayList<>();
        }

        // 统一刷新 URL
        outfits.forEach(outfitService::refreshOutfitUrls);

        // 批量获取作者信息
        Set<Long> userIds = outfits.stream().map(Outfit::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            if (users != null) {
                userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
            }
        }

        final Map<Long, User> finalUserMap = userMap;
        return outfits.stream().map(o -> {
            OutfitVO vo = new OutfitVO();
            BeanUtils.copyProperties(o, vo);
            User author = finalUserMap.get(o.getUserId());
            if (author != null) {
                String dispName = (author.getNickname() != null && !author.getNickname().trim().isEmpty()) 
                                  ? author.getNickname() : author.getUsername();
                vo.setUsername(dispName);
                vo.setUserAvatar(author.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
