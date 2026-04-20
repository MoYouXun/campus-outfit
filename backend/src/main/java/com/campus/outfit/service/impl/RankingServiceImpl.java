package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.User;
import com.campus.outfit.mapper.UserMapper;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
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
    public List<Outfit> getHotRanking(String gender, int limit) {
        String key = (gender != null && !gender.isEmpty()) ? RANK_HOT_KEY + ":" + gender.toUpperCase() : RANK_HOT_KEY;
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        if (ids == null || ids.isEmpty()) {
            return fallbackRanking(gender, limit);
        }
        
        List<Long> outfitIds = ids.stream().map(obj -> Long.valueOf(obj.toString())).collect(Collectors.toList());
        List<Outfit> rawList = outfitService.listByIds(outfitIds);
        
        // 1. 内存重排，保证按 Redis 分数顺序
        Map<Long, Outfit> map = rawList.stream().collect(Collectors.toMap(Outfit::getId, o -> o));
        List<Outfit> sortedList = outfitIds.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
        
        // 2. 计算排名趋势 (对比 :prev 集合)
        String prevKey = key + ":prev";
        for (int i = 0; i < sortedList.size(); i++) {
            Outfit outfit = sortedList.get(i);
            Long prevRank = redisTemplate.opsForZSet().reverseRank(prevKey, outfit.getId().toString());
            if (prevRank == null) {
                outfit.setRankTrend(999); // 新上榜
            } else if (prevRank > i) {
                outfit.setRankTrend(1); // 上升
            } else if (prevRank < i) {
                outfit.setRankTrend(-1); // 下降
            } else {
                outfit.setRankTrend(0); // 持平
            }
        }
        fillAuthorInfo(sortedList);
        sortedList.forEach(outfitService::refreshOutfitUrls);
        return sortedList;
    }

    @Override
    public List<Outfit> getStyleRanking(String style, String gender, int limit) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .apply("JSON_CONTAINS(style_tags, {0})", "\"" + style + "\"")
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED");
        if (gender != null && !gender.isEmpty()) {
            Integer genderVal = "MALE".equalsIgnoreCase(gender) ? 1 : 2;
            wrapper.and(w -> w.eq(Outfit::getGender, genderVal).or().eq(Outfit::getGender, 0));
        }
        wrapper.orderByDesc(Outfit::getLikeCount).last("LIMIT " + limit);
        List<Outfit> list = outfitService.list(wrapper);
        fillAuthorInfo(list);
        list.forEach(outfitService::refreshOutfitUrls);
        return list;
    }

    @Override
    public List<Outfit> getSchoolRanking(String school, String gender, int limit) {
        return fallbackRanking(gender, limit);
    }

    @Override
    public void refreshRankings() {
        List<Outfit> allOutfits = outfitService.list(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true).eq(Outfit::getStatus, "PUBLISHED"));
                
        // 备份旧榜单用于计算趋势
        if(Boolean.TRUE.equals(redisTemplate.hasKey(RANK_HOT_KEY))) redisTemplate.rename(RANK_HOT_KEY, RANK_HOT_KEY + ":prev");
        if(Boolean.TRUE.equals(redisTemplate.hasKey(RANK_HOT_KEY + ":MALE"))) redisTemplate.rename(RANK_HOT_KEY + ":MALE", RANK_HOT_KEY + ":MALE:prev");
        if(Boolean.TRUE.equals(redisTemplate.hasKey(RANK_HOT_KEY + ":FEMALE"))) redisTemplate.rename(RANK_HOT_KEY + ":FEMALE", RANK_HOT_KEY + ":FEMALE:prev");

        for (Outfit outfit : allOutfits) {
            // Hacker News 算法变体：时间衰减
            long hours = Duration.between(outfit.getCreateTime(), LocalDateTime.now()).toHours();
            double decay = Math.pow((hours + 2.0), 1.5);
            double score = (outfit.getLikeCount() * 1.0 + outfit.getCommentCount() * 1.5) / decay;

            String idStr = outfit.getId().toString();
            redisTemplate.opsForZSet().add(RANK_HOT_KEY, idStr, score);
            if (Integer.valueOf(1).equals(outfit.getGender()) || Integer.valueOf(0).equals(outfit.getGender())) {
                redisTemplate.opsForZSet().add(RANK_HOT_KEY + ":MALE", idStr, score);
            }
            if (Integer.valueOf(2).equals(outfit.getGender()) || Integer.valueOf(0).equals(outfit.getGender())) {
                redisTemplate.opsForZSet().add(RANK_HOT_KEY + ":FEMALE", idStr, score);
            }
        }
        // 裁剪保留前100
        redisTemplate.opsForZSet().removeRange(RANK_HOT_KEY, 0, -101);
        redisTemplate.opsForZSet().removeRange(RANK_HOT_KEY + ":MALE", 0, -101);
        redisTemplate.opsForZSet().removeRange(RANK_HOT_KEY + ":FEMALE", 0, -101);
    }

    private List<Outfit> fallbackRanking(String gender, int limit) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED");
        if (gender != null && !gender.isEmpty()) {
            Integer genderVal = "MALE".equalsIgnoreCase(gender) ? 1 : 2;
            wrapper.and(w -> w.eq(Outfit::getGender, genderVal).or().eq(Outfit::getGender, 0));
        }
        wrapper.orderByDesc(Outfit::getLikeCount).last("LIMIT " + limit);
        List<Outfit> list = outfitService.list(wrapper);
        fillAuthorInfo(list);
        list.forEach(outfitService::refreshOutfitUrls);
        return list;
    }

    private void fillAuthorInfo(List<Outfit> outfits) {
        if (outfits == null || outfits.isEmpty()) return;
        List<Long> userIds = outfits.stream().map(Outfit::getUserId).distinct().collect(Collectors.toList());
        if(userIds.isEmpty()) return;
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        for (Outfit outfit : outfits) {
            User user = userMap.get(outfit.getUserId());
            if (user != null) {
                outfit.setAuthorName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                outfit.setAuthorAvatar(user.getAvatar());
            }
        }
    }
}
