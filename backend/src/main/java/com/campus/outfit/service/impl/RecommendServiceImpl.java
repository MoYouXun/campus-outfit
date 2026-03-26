package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.FavoriteMapper;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RecommendService;
import com.campus.outfit.service.WeatherService;
import com.campus.outfit.vo.OutfitVO;
import com.campus.outfit.vo.WeatherInfoVO;
import com.campus.outfit.service.AiService;
import com.campus.outfit.dto.AiRecommendationResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl implements RecommendService {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private AiService aiService;

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Override
    public IPage<OutfitVO> recommendBySeason(String city, Double latitude, Double longitude, int page, int size) {
        WeatherInfoVO weather = null;
        int temp = 20;

        try {
            if (latitude != null && longitude != null) {
                weather = weatherService.getWeatherAndDressIndexByLocation(latitude, longitude);
            } else {
                weather = weatherService.getWeatherAndDressIndex(city);
            }
            if (weather != null && weather.getTemperature() != null) {
                String tempStr = weather.getTemperature().replace("°C", "");
                temp = Integer.parseInt(tempStr);
            }
        } catch (Exception e) {
            System.err.println("获取天气信息失败，使用默认温度推荐: " + e.getMessage());
        }

        // 先尝试按温度区间筛选
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED");

        if (temp < 10) {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "冷")
                    .or().like(Outfit::getTemperatureRange, "冬")
                    .or().like(Outfit::getSeason, "冬"));
        } else if (temp < 20) {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "凉")
                    .or().like(Outfit::getTemperatureRange, "春秋")
                    .or().like(Outfit::getSeason, "春")
                    .or().like(Outfit::getSeason, "秋"));
        } else {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "热")
                    .or().like(Outfit::getTemperatureRange, "夏")
                    .or().like(Outfit::getSeason, "夏"));
        }

        wrapper.orderByDesc(Outfit::getLikeCount);
        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);

        // 如果按温度筛选没有结果，回退到展示所有已发布穿搭
        if (outfitPage.getRecords().isEmpty()) {
            LambdaQueryWrapper<Outfit> fallbackWrapper = new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getLikeCount);
            outfitPage = outfitService.page(new Page<>(page, size), fallbackWrapper);
        }

        String reason;
        if (weather != null) {
            reason = String.format("今日%s %s，气温 %s，%s",
                    weather.getLocation(), weather.getWeatherDesc(),
                    weather.getTemperature(), weather.getSuggestion());
        } else {
            reason = "根据季节为您推荐精选穿搭";
        }

        return convertPage(outfitPage, reason);
    }

    @Override
    public IPage<OutfitVO> recommendByOccasion(String occasion, int page, int size) {
        // 先尝试精确匹配 occasion 字段
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .like(Outfit::getOccasion, occasion)
                .orderByDesc(Outfit::getLikeCount);

        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);

        // 如果没有结果，回退到展示所有已发布穿搭
        if (outfitPage.getRecords().isEmpty()) {
            LambdaQueryWrapper<Outfit> fallbackWrapper = new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getLikeCount);
            outfitPage = outfitService.page(new Page<>(page, size), fallbackWrapper);
        }

        return convertPage(outfitPage, "为您推荐适合 " + occasion + " 场景的精选穿搭");
    }

    @Override
    public IPage<OutfitVO> recommendByStyle(Long userId, int page, int size) {
        // 获取用户最近收藏的穿搭
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit 5"));

        if (favorites.isEmpty()) {
            return convertPage(outfitService.page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getFavCount)), "为您推荐当前最受欢迎的时尚风格");
        }

        List<Long> outfitIds = favorites.stream().map(Favorite::getOutfitId).collect(Collectors.toList());
        List<Outfit> recentOutfits = outfitService.listByIds(outfitIds);

        Set<String> userStyleTags = new HashSet<>();
        recentOutfits.forEach(outfit -> {
            if (outfit.getStyleTags() != null) {
                userStyleTags.addAll(outfit.getStyleTags());
            }
        });

        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .notIn(Outfit::getId, outfitIds);

        if (!userStyleTags.isEmpty()) {
            wrapper.and(w -> {
                boolean first = true;
                for (String style : userStyleTags) {
                    if (first) {
                        w.like(Outfit::getStyleTags, style);
                        first = false;
                    } else {
                        w.or().like(Outfit::getStyleTags, style);
                    }
                }
            });
        }

        wrapper.orderByDesc(Outfit::getLikeCount);
        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);

        // 如果没有结果，回退
        if (outfitPage.getRecords().isEmpty()) {
            outfitPage = outfitService.page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getLikeCount));
        }

        return convertPage(outfitPage, "根据你最近喜欢的风格为您推荐相似穿搭");
    }

    @Override
    public IPage<OutfitVO> recommendPersonalized(Long userId, String city, Double latitude, Double longitude, String scenario, int page, int size) {
        WeatherInfoVO weather = null;
        try {
            if (latitude != null && longitude != null) {
                weather = weatherService.getWeatherAndDressIndexByLocation(latitude, longitude);
            } else {
                weather = weatherService.getWeatherAndDressIndex(city);
            }
        } catch (Exception e) {
            System.err.println("获取天气信息失败: " + e.getMessage());
        }

        // 获取用户私有衣橱
        List<Outfit> userWardrobe = outfitService.list(new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getUserId, userId)
                .eq(Outfit::getIsDeleted, 0));

        // 构造 Prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("当前位置的天气简况：")
                .append(weather != null ? weather.getWeatherDesc() + ", 气温 " + weather.getTemperature() : "未知天气")
                .append("。\n");
        promptBuilder.append("用户当前所处场景或说明：")
                .append((scenario != null && !scenario.isEmpty()) ? scenario : "日常校园出行")
                .append("。\n");
        
        promptBuilder.append("这是用户的私人衣橱列表，格式为[单品ID, 特征, 风格, 颜色]：\n");
        if (userWardrobe == null || userWardrobe.isEmpty()) {
            promptBuilder.append("（用户当前没有上传任何私服）\n");
        } else {
            for (Outfit o : userWardrobe) {
                promptBuilder.append(String.format("- ID:%d, 特征:%s, 风格:%s, 颜色:%s\n",
                        o.getId(), o.getItemKeywords(), o.getStyleTags(), o.getColorTags()));
            }
        }
        promptBuilder.append("请为用户搭配一套合适的穿搭，重点从上述衣橱中挑选。无合适可选时返回空数组[]并提供 searchTags。");

        try {
            AiRecommendationResult result = aiService.recommendOutfit(promptBuilder.toString());
            String reason = (result != null && result.getReasoning() != null) ? result.getReasoning() : "为您找到的场景绝佳穿搭";
            
            // 1. 尝试使用私有衣橱的推荐
            if (result != null && result.getOutfitIds() != null && !result.getOutfitIds().isEmpty()) {
                List<Outfit> matchedOutfits = outfitService.listByIds(result.getOutfitIds());
                if (!matchedOutfits.isEmpty()) {
                    Page<Outfit> outfitPage = new Page<>(1, matchedOutfits.size());
                    outfitPage.setRecords(matchedOutfits);
                    outfitPage.setTotal(matchedOutfits.size());
                    return convertPage(outfitPage, "【AI私人衣橱】" + reason);
                }
            }

            // 2. 如果私服无精选，尝试根据给出的 tags 按全站公库查找兜底
            if (result != null && result.getSearchTags() != null && !result.getSearchTags().isEmpty()) {
                LambdaQueryWrapper<Outfit> fallbackWrapper = new LambdaQueryWrapper<>();
                fallbackWrapper.eq(Outfit::getStatus, "PUBLISHED");
                fallbackWrapper.eq(Outfit::getIsPublic, true);
                fallbackWrapper.and(w -> {
                    for (String tag : result.getSearchTags()) {
                        w.or().like(Outfit::getDescription, tag)
                         .or().like(Outfit::getTitle, tag)
                         .or().like(Outfit::getStyleTags, tag)
                         .or().like(Outfit::getItemKeywords, tag);
                    }
                });
                fallbackWrapper.orderByDesc(Outfit::getLikeCount);
                
                Page<Outfit> fallbackPage = new Page<>(page, size);
                IPage<Outfit> fallbackResult = outfitService.page(fallbackPage, fallbackWrapper);
                if (!fallbackResult.getRecords().isEmpty()) {
                    return convertPage(fallbackResult, "【AI全站甄选】" + reason);
                }
            }
            
            // 3. 全局都查不到任何相关词？用 reason 作为兜底信息，并退化为天气/季节通用推挽
            IPage<OutfitVO> finalFallback = recommendBySeason(city, latitude, longitude, page, size);
            if(finalFallback.getRecords() != null) {
                for (OutfitVO v : finalFallback.getRecords()) {
                    v.setRecommendReason("【AI热力推荐】" + reason);
                }
            }
            return finalFallback;
            
        } catch (Exception e) {
            System.err.println("AI推荐解析失败，回退到季节推荐: " + e.getMessage());
        }

        // 终极回退到通用季候推荐
        return recommendBySeason(city, latitude, longitude, page, size);
    }

    private IPage<OutfitVO> convertPage(IPage<Outfit> page, String reason) {
        List<OutfitVO> voList = page.getRecords().stream().map(o -> {
            try {
                outfitService.refreshOutfitUrls(o);
            } catch (Exception ignored) {}
            OutfitVO vo = new OutfitVO();
            BeanUtils.copyProperties(o, vo);
            vo.setRecommendReason(reason);
            return vo;
        }).collect(Collectors.toList());

        Page<OutfitVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}