package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.OutfitTopic;
import com.campus.outfit.mapper.OutfitMapper;
import com.campus.outfit.mapper.OutfitTopicMapper;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.FavoriteService;
import com.campus.outfit.service.LikeService;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.TopicService;
import com.campus.outfit.enums.OutfitStatusEnum;
import com.campus.outfit.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OutfitServiceImpl extends ServiceImpl<OutfitMapper, Outfit> implements OutfitService {

    @Autowired
    private MinioService minioService;

    @Autowired
    private AiService aiService;

    @Autowired
    private TopicService topicService;

    @Autowired
    private OutfitTopicMapper outfitTopicMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Override
    public IPage<Outfit> getPublicOutfits(int page, int size) {
        return getPublicOutfits(page, size, "latest", null, null, null);
    }

    @Override
    public IPage<Outfit> getPublicOutfits(int page, int size, String sortBy, Long topicId, Long targetUserId, Long currentUserId) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, OutfitStatusEnum.PUBLISHED.getCode());
        
        if (targetUserId != null) {
            wrapper.eq(Outfit::getUserId, targetUserId);
        }
        
        if (topicId != null) {
            List<com.campus.outfit.entity.OutfitTopic> relations = outfitTopicMapper.selectList(
                new LambdaQueryWrapper<com.campus.outfit.entity.OutfitTopic>().eq(com.campus.outfit.entity.OutfitTopic::getTopicId, topicId)
            );
            List<Long> outfitIds = relations.stream().map(com.campus.outfit.entity.OutfitTopic::getOutfitId).collect(java.util.stream.Collectors.toList());
            if (outfitIds.isEmpty()) {
                return new Page<>(page, size);
            }
            wrapper.in(Outfit::getId, outfitIds);
        }

        if ("hot".equalsIgnoreCase(sortBy)) {
            wrapper.orderByDesc(Outfit::getLikeCount)
                   .orderByDesc(Outfit::getCommentCount)
                   .orderByDesc(Outfit::getCreateTime);
        } else {
            wrapper.orderByDesc(Outfit::getCreateTime);
        }
        
        IPage<Outfit> resultPage = page(new Page<>(page, size), wrapper);
        resultPage.getRecords().forEach(item -> {
            refreshOutfitUrls(item);
            populateLikedAndFavorited(item, currentUserId);
        });
        return resultPage;
    }

    private void populateLikedAndFavorited(Outfit outfit, Long currentUserId) {
        if (currentUserId != null && outfit != null) {
            outfit.setLiked(likeService.isLiked(currentUserId, outfit.getId()));
            outfit.setFavorited(favoriteService.isFavorited(currentUserId, outfit.getId()));
        }
    }

    @Override
    public IPage<Outfit> getFollowingOutfits(List<Long> followingIds, int page, int size, Long currentUserId) {
        if (followingIds == null || followingIds.isEmpty()) {
            return new Page<>(page, size);
        }
        
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, OutfitStatusEnum.PUBLISHED.getCode())
                .in(Outfit::getUserId, followingIds)
                .orderByDesc(Outfit::getCreateTime);
        
        IPage<Outfit> resultPage = page(new Page<>(page, size), wrapper);
        resultPage.getRecords().forEach(item -> {
            refreshOutfitUrls(item);
            populateLikedAndFavorited(item, currentUserId);
        });
        return resultPage;
    }

    @Override
    public Result<AiAnalysisResult> uploadAndAnalyze(List<MultipartFile> files) {
        log.info("收到上传请求，文件数量: {}", files != null ? files.size() : 0);
        if (files == null || files.isEmpty()) {
            return Result.fail("请上传图片");
        }

        try {
            log.info("开始处理文件上传...");
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                log.info("处理文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
                String objectName = minioService.uploadImage(file);
                log.info("文件上传成功，对象名: {}", objectName);
                String url = minioService.getImageUrl(objectName);
                log.info("获取图片URL成功: {}", url);
                urls.add(url);
            }

            log.info("开始调用AI分析...");
            // 针对第一张图片进行 AI 分析（或可改为合成后分析，此处简化取首张）
            AiAnalysisResult analysis = aiService.analyzeOutfit(files.get(0).getBytes());
            log.info("AI分析成功");
            
            // 将文件列表 URL 传回前端，方便后续发布阶段保存
            analysis.setImageUrls(urls);
            return Result.success(analysis);
        } catch (Exception e) {
            log.error("分析失败，异常信息: ", e);
            return Result.fail("分析失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<String> publishOutfit(Outfit outfit) {
        try {
            log.info("正在发布穿搭: {}", outfit.getTitle());
            // 设置默认值
            outfit.setLikeCount(0);
            outfit.setCommentCount(0);
            outfit.setFavCount(0);
            outfit.setViewCount(0);
            outfit.setStatus(OutfitStatusEnum.PUBLISHED.getCode());
            
            // 设置缩略图URL
            if (outfit.getImageUrls() != null && !outfit.getImageUrls().isEmpty()) {
                outfit.setThumbnailUrl(outfit.getImageUrls().get(0));
            }
            
            // 尝试将关键信息的 JSON 备份到 ai_analysis 字段（如果前端没传）
            if (outfit.getAiAnalysis() == null) {
                try {
                    Map<String, Object> aiMap = new HashMap<>();
                    aiMap.put("styleTags", outfit.getStyleTags());
                    aiMap.put("colorTags", outfit.getColorTags());
                    aiMap.put("itemKeywords", outfit.getItemKeywords());
                    aiMap.put("suggestion", outfit.getDescription());
                    outfit.setAiAnalysis(objectMapper.writeValueAsString(aiMap));
                } catch (Exception e) {
                    log.warn("序列化 AI 分析结果失败: {}", e.getMessage());
                }
            }
            
            save(outfit);

            // 关联话题
            if (outfit.getTopicId() != null) {
                OutfitTopic ot = new OutfitTopic();
                ot.setOutfitId(outfit.getId());
                ot.setTopicId(outfit.getTopicId());
                outfitTopicMapper.insert(ot);
                topicService.incrementOutfitCount(outfit.getTopicId());
            }

            log.info("发布成功，ID: {}", outfit.getId());
            return Result.success("发布成功");
        } catch (Exception e) {
            log.error("发布失败，异常: ", e);
            return Result.fail("发布失败，数据库保存异常：" + e.getMessage());
        }
    }

    @Override
    public IPage<Outfit> getMyOutfits(Long userId, int page, int size) {
        IPage<Outfit> resultPage = page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getUserId, userId)
                .orderByDesc(Outfit::getCreateTime));
        resultPage.getRecords().forEach(this::refreshOutfitUrls);
        return resultPage;
    }

    @Override
    @Transactional
    public Result<String> deleteOutfit(Long id, Long userId) {
        try {
            // 检查帖子是否存在
            Outfit outfit = getById(id);
            if (outfit == null) {
                return Result.fail("帖子不存在");
            }

            // 检查是否是帖子所有者
            if (!outfit.getUserId().equals(userId)) {
                return Result.fail("无权限删除该帖子");
            }

            // 删除与话题的关联
            List<OutfitTopic> relations = outfitTopicMapper.selectList(
                new LambdaQueryWrapper<OutfitTopic>().eq(OutfitTopic::getOutfitId, id)
            );
            for (OutfitTopic relation : relations) {
                outfitTopicMapper.deleteById(relation.getId());
                // 更新话题的穿搭数量
                topicService.decrementOutfitCount(relation.getTopicId());
            }

            // 删除帖子（逻辑删除）
            removeById(id);

            return Result.success("帖子删除成功");
        } catch (Exception e) {
            log.error("帖子删除失败: ", e);
            return Result.fail("帖子删除失败: " + e.getMessage());
        }
    }

    @Override
    public Result<String> incrementViewCount(Long id) {
        try {
            // 检查帖子是否存在
            Outfit outfit = getById(id);
            if (outfit == null) {
                return Result.fail("帖子不存在");
            }

            // 改用 Redis 进行浏览量自增
            String key = "outfit:view_count:" + id;
            redisTemplate.opsForValue().increment(key);
            log.info("帖子 {} 浏览量已增加到 Redis", id);

            return Result.success("浏览计数增加成功");
        } catch (Exception e) {
            log.error("增加浏览量失败: {}", e.getMessage());
            return Result.fail("浏览计数增加失败: " + e.getMessage());
        }
    }

    @Override
    public Outfit getById(java.io.Serializable id) {
        Outfit outfit = super.getById(id);
        refreshOutfitUrls(outfit);
        return outfit;
    }

    @Override
    public void refreshOutfitUrls(Outfit outfit) {
        if (outfit == null) return;
        try {
            if (outfit.getThumbnailUrl() != null) {
                String objName = extractObjectName(outfit.getThumbnailUrl());
                if (objName != null) {
                    outfit.setThumbnailUrl(minioService.getImageUrl(objName));
                }
            }
            if (outfit.getImageUrls() != null && !outfit.getImageUrls().isEmpty()) {
                List<String> newUrls = new ArrayList<>();
                for (String url : outfit.getImageUrls()) {
                    String objName = extractObjectName(url);
                    if (objName != null) {
                        newUrls.add(minioService.getImageUrl(objName));
                    } else {
                        newUrls.add(url);
                    }
                }
                outfit.setImageUrls(newUrls);
            }
        } catch (Exception e) {
            log.warn("刷新穿搭图片URL失败: {}", e.getMessage());
        }
    }

    private String extractObjectName(String url) {
        if (url == null || !url.contains("/")) return null;
        try {
            String path = url.split("\\?")[0];
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return null;
        }
    }
}
