package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.FavoriteMapper;
import com.campus.outfit.service.FavoriteService;
import com.campus.outfit.service.LikeService;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.RecommendService;
import com.campus.outfit.service.WeatherService;
import com.campus.outfit.vo.OutfitVO;
import com.campus.outfit.vo.WeatherInfoVO;
import com.campus.outfit.dto.AiRecommendationResult;
import com.campus.outfit.utils.DoubaoUtil;
import com.campus.outfit.utils.SeedreamUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl implements RecommendService {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private DoubaoUtil doubaoUtil;

    @Autowired
    private SeedreamUtil seedreamUtil;

    @Autowired
    private MinioService minioService;

    @Autowired
    private ObjectMapper objectMapper;

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
                Matcher matcher = Pattern.compile("-?\\d+").matcher(weather.getTemperature());
                if (matcher.find()) {
                    temp = Integer.parseInt(matcher.group());
                }
            }
        } catch (Exception e) {
            System.err.println("获取天气信息失败: " + e.getMessage());
        }

        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED");

        if (temp < 10) {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "冷").or().like(Outfit::getSeason, "冬"));
        } else if (temp < 20) {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "凉").or().like(Outfit::getSeason, "春秋"));
        } else {
            wrapper.and(w -> w.like(Outfit::getTemperatureRange, "热").or().like(Outfit::getSeason, "夏"));
        }

        wrapper.orderByDesc(Outfit::getLikeCount);
        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);

        String reason = (weather != null) ? String.format("今日%s %s，气温 %s", weather.getLocation(), weather.getWeatherDesc(), weather.getTemperature()) : "甄选推荐";
        return convertPage(outfitPage, reason, currentUserId);
    }

    @Override
    public IPage<OutfitVO> recommendByOccasion(String occasion, int page, int size, Long currentUserId) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .like(Outfit::getOccasion, occasion)
                .orderByDesc(Outfit::getLikeCount);

        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);
        if (outfitPage.getRecords().isEmpty()) {
            outfitPage = outfitService.page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getLikeCount));
        }
        return convertPage(outfitPage, "适合 " + occasion + " 的穿搭", currentUserId);
    }

    @Override
    public IPage<OutfitVO> recommendByStyle(Long userId, int page, int size) {
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .last("limit 5"));

        if (favorites.isEmpty()) {
            return convertPage(outfitService.page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                    .eq(Outfit::getIsPublic, true)
                    .eq(Outfit::getStatus, "PUBLISHED")
                    .orderByDesc(Outfit::getFavCount)), "甄选热门风格", userId);
        }

        List<Long> outfitIds = favorites.stream().map(Favorite::getOutfitId).collect(Collectors.toList());
        List<Outfit> recentOutfits = outfitService.listByIds(outfitIds);
        Set<String> userStyleTags = new HashSet<>();
        recentOutfits.forEach(o -> { if (o.getStyleTags() != null) userStyleTags.addAll(o.getStyleTags()); });

        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED")
                .notIn(Outfit::getId, outfitIds);

        if (!userStyleTags.isEmpty()) {
            wrapper.and(w -> {
                boolean first = true;
                for (String style : userStyleTags) {
                    if (first) { w.like(Outfit::getStyleTags, style); first = false; }
                    else { w.or().like(Outfit::getStyleTags, style); }
                }
            });
        }

        wrapper.orderByDesc(Outfit::getLikeCount);
        IPage<Outfit> outfitPage = outfitService.page(new Page<>(page, size), wrapper);
        return convertPage(outfitPage, "为您定制的个人风格推荐", userId);
    }

    @Override
    public AiRecommendationResult recommendPersonalized(Long userId, MultipartFile image, String scenario) {
        try {
            String targetObjectName = minioService.uploadImage(image);
            String targetBase64 = minioService.getImageAsBase64(targetObjectName);

            String analysisJson = doubaoUtil.analyzeWithWardrobe(targetBase64, userId);
            JsonNode rootNode = objectMapper.readTree(analysisJson);

            AiRecommendationResult result = new AiRecommendationResult();
            result.setReasoning(rootNode.path("reasoning").asText());
            result.setStyleType(rootNode.path("styleType").asText());
            result.setOccasion(rootNode.path("occasion").asText());

            List<String> fusionBase64List = new ArrayList<>();
            fusionBase64List.add(targetBase64);

            JsonNode itemsNode = rootNode.path("recommendedItems");
            List<String> recommendedNames = new ArrayList<>();
            if (itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    String objectName = node.asText();
                    recommendedNames.add(objectName);
                    try {
                        fusionBase64List.add(minioService.getImageAsBase64(objectName));
                    } catch (Exception ignored) {}
                }
            }
            result.setRecommendedItems(recommendedNames);

            String fusionPrompt = String.format("生成一张精美的穿搭效果图。风格：%s。场景：%s。将用户原始图片中的人物与推荐的衣柜单品完美融合，展现整体搭配效果。", 
                    result.getStyleType(), result.getOccasion());
            
            String generatedImageUrl = seedreamUtil.generateFusionImage(fusionPrompt, fusionBase64List);
            result.setImageUrl(generatedImageUrl);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI 个性化推荐流水线执行失败: " + e.getMessage());
        }
    }

    private IPage<OutfitVO> convertPage(IPage<Outfit> page, String reason, Long currentUserId) {
        List<OutfitVO> voList = page.getRecords().stream().map(o -> {
            try {
                outfitService.refreshOutfitUrls(o);
            } catch (Exception ignored) {}
            OutfitVO vo = new OutfitVO();
            BeanUtils.copyProperties(o, vo);
            
            if (currentUserId != null) {
                vo.setLiked(likeService.isLiked(currentUserId, o.getId()));
                vo.setFavorited(favoriteService.isFavorited(currentUserId, o.getId()));
            }

            boolean isFallbackMsg = reason.contains("正在为您") || reason.contains("暂时小憩");
            String finalReason = isFallbackMsg ? reason : "【AI推荐】" + reason;
            if (!isFallbackMsg && o.getStyleTags() != null && !o.getStyleTags().isEmpty()) {
                finalReason += " — " + o.getStyleTags().get(0) + "风";
            }
            vo.setRecommendReason(finalReason);

            Random random = new Random();
            int baseScore = 85 + random.nextInt(10);
            vo.setMatchScore(Math.min(99, baseScore));

            List<String> labels = new ArrayList<>();
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