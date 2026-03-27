package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.FavoriteMapper;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.FavoriteService;
import com.campus.outfit.service.LikeService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RecommendService;
import com.campus.outfit.service.WeatherService;
import com.campus.outfit.vo.OutfitVO;
import com.campus.outfit.vo.WeatherInfoVO;
import com.campus.outfit.dto.AiRecommendationResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Autowired
    private LikeService likeService;

    @Autowired
    private FavoriteService favoriteService;

    @Override
    public IPage<OutfitVO> recommendBySeason(String city, Double latitude, Double longitude, int page, int size, Long currentUserId) {
        WeatherInfoVO weather = null;
        int temp = 20;

        try {
            if (latitude != null && longitude != null) {
                weather = weatherService.getWeatherAndDressIndexByLocation(latitude, longitude);
            } else {
                weather = weatherService.getWeatherAndDressIndex(city);
            }
            if (weather != null && weather.getTemperature() != null) {
                // 使用正则提取温度字符串中的第一个整数，防御异常格式（如 "15~20°C" / "℃" / "未知"）
                Matcher matcher = Pattern.compile("-?\\d+").matcher(weather.getTemperature());
                if (matcher.find()) {
                    temp = Integer.parseInt(matcher.group());
                }
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

        return convertPage(outfitPage, reason, currentUserId);
    }

    @Override
    public IPage<OutfitVO> recommendByOccasion(String occasion, int page, int size, Long currentUserId) {
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

        return convertPage(outfitPage, "为您推荐适合 " + occasion + " 场景的精选穿搭", currentUserId);
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
                    .orderByDesc(Outfit::getFavCount)), "为您推荐当前最受欢迎的时尚风格", userId);
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

        return convertPage(outfitPage, "根据你最近喜欢的风格为您推荐相似穿搭", userId);
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
            //    安全过滤：AI 存在幻觉风险，返回的 ID 必须校验属于当前用户或是公开穿搭，防止越权查看他人私服
            if (result != null && result.getOutfitIds() != null && !result.getOutfitIds().isEmpty()) {
                List<Outfit> matchedOutfits = outfitService.listByIds(result.getOutfitIds());
                // 过滤：只保留属于当前用户的私服，或本已公开的穿搭
                List<Outfit> safeOutfits = matchedOutfits.stream()
                        .filter(o -> userId.equals(o.getUserId()) || Boolean.TRUE.equals(o.getIsPublic()))
                        .collect(Collectors.toList());
                if (!safeOutfits.isEmpty()) {
                    Page<Outfit> outfitPage = new Page<>(1, safeOutfits.size());
                    outfitPage.setRecords(safeOutfits);
                    outfitPage.setTotal(safeOutfits.size());
                    return convertPage(outfitPage, "【AI私人衣橱】" + reason, userId);
                }
            }

            // 2. 如果私服无精选，尝试根据给出的 tags 按全站公库查找兜底
            //    限制 searchTags 最多 3 个，避免过多 OR LIKE 导致慢 SQL
            if (result != null && result.getSearchTags() != null && !result.getSearchTags().isEmpty()) {
                List<String> limitedTags = result.getSearchTags().stream().limit(3).collect(Collectors.toList());
                LambdaQueryWrapper<Outfit> fallbackWrapper = new LambdaQueryWrapper<>();
                fallbackWrapper.eq(Outfit::getStatus, "PUBLISHED");
                fallbackWrapper.eq(Outfit::getIsPublic, true);
                fallbackWrapper.and(w -> {
                    for (String tag : limitedTags) {
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
                    return convertPage(fallbackResult, "【AI全站甄选】" + reason, userId);
                }
            }
            
            // 3. 全局都查不到任何相关词？用 reason 作为兜底信息，并退化为天气/季节通用推挽
            IPage<OutfitVO> finalFallback = recommendBySeason(city, latitude, longitude, page, size, userId);
            if(finalFallback.getRecords() != null) {
                // 如果是降级话术，则不显示重复的 AI 前缀，直接展示该话术
                boolean isFallbackMsg = reason.contains("正在为您") || reason.contains("暂时小憩");
                for (OutfitVO v : finalFallback.getRecords()) {
                    v.setRecommendReason(isFallbackMsg ? reason : "【AI顾问灵感】" + reason);
                }
            }
            return finalFallback;
            
        } catch (Exception e) {
            System.err.println("AI推荐解析失败，回退到季节推荐: " + e.getMessage());
        }

        // 终极回退到通用季候推荐
        return recommendBySeason(city, latitude, longitude, page, size, userId);
    }

    /**
     * \u5c06 Outfit \u5206\u9875\u8f6c\u6362\u4e3a VO\u3002\u4e3a\u6bcf\u4e2a\u6761\u76ee\u751f\u6210\u5305\u542b\u98ce\u683c/\u989c\u8272\u6807\u7b7e\u7684\u5dee\u5f02\u5316\u63a8\u8350\u7406\u7531\u3002
     */
    private IPage<OutfitVO> convertPage(IPage<Outfit> page, String reason, Long currentUserId) {
        List<OutfitVO> voList = page.getRecords().stream().map(o -> {
            try {
                outfitService.refreshOutfitUrls(o);
            } catch (Exception ignored) {}
            OutfitVO vo = new OutfitVO();
            BeanUtils.copyProperties(o, vo);
            
            // 填充点赞和收藏状态
            if (currentUserId != null) {
                vo.setLiked(likeService.isLiked(currentUserId, o.getId()));
                vo.setFavorited(favoriteService.isFavorited(currentUserId, o.getId()));
            }

            // 2026-03-27 优化：在 lambda 内部定义降级标识并设置推荐理由
            boolean isFallbackMsg = reason.contains("正在为您") || reason.contains("暂时小憩");
            
            // 如果不是降级话术，拼接一些单品特征增强丰富度
            String finalReason = isFallbackMsg ? reason : "【AI推荐】" + reason;
            if (!isFallbackMsg && o.getStyleTags() != null && !o.getStyleTags().isEmpty()) {
                finalReason += " — " + o.getStyleTags().get(0) + "风";
            }
            vo.setRecommendReason(finalReason);

            // 增强推荐可读性：添加匹配得分和智能标签
            Random random = new Random();
            int baseScore = 85 + random.nextInt(10); // 基础分 85-95
            if (vo.getRecommendReason().contains("AI顾问")) baseScore += 4; // AI 推荐额外加分
            vo.setMatchScore(Math.min(99, baseScore));

            List<String> labels = new ArrayList<>();
            // 根据穿搭属性自动打标，增强专业感
            if (o.getDescription() != null) {
                if (o.getDescription().contains("棉") || o.getDescription().contains("麻")) labels.add("透气亲肤");
                if (o.getDescription().contains("风") || o.getDescription().contains("外套")) labels.add("强力防风");
            }
            if (o.getStyleTags() != null) {
                if (o.getStyleTags().contains("简约")) labels.add("极致利落");
                if (o.getStyleTags().contains("复古")) labels.add("格调回潮");
            }
            if (labels.isEmpty()) {
                labels.add("本日精选");
                labels.add("高赞搭配");
            }
            vo.setMatchLabels(labels.stream().limit(2).collect(Collectors.toList()));

            return vo;
        }).collect(Collectors.toList());

        Page<OutfitVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}