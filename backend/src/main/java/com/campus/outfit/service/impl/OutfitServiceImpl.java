package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.exception.BusinessException;
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
import com.campus.outfit.utils.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public IPage<Outfit> getPublicOutfits(int page, int size) {
        return getPublicOutfits(page, size, "latest", null, null, null);
    }

    @Override
    public IPage<Outfit> getPublicOutfits(int page, int size, String sortBy, Long topicId, Long targetUserId, Long currentUserId) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getIsPublic, true)
                .eq(Outfit::getStatus, "PUBLISHED");
        
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
                .eq(Outfit::getStatus, "PUBLISHED")
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
        System.out.println("[DEBUG] 收到上传请求，文件数量: " + (files != null ? files.size() : 0));
        if (files == null || files.isEmpty()) {
            return Result.fail("请上传图片");
        }

        try {
            System.out.println("[DEBUG] 开始处理文件上传...");
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                System.out.println("[DEBUG] 处理文件: " + file.getOriginalFilename() + ", 大小: " + file.getSize() + " bytes");
                String objectName = minioService.uploadImage(file);
                System.out.println("[DEBUG] 文件上传成功，对象名: " + objectName);
                String url = minioService.getImageUrl(objectName);
                System.out.println("[DEBUG] 获取图片URL成功: " + url);
                urls.add(url);
            }

            System.out.println("[DEBUG] 开始调用AI分析...");
            // 针对第一张图片进行 AI 分析（或可改为合成后分析，此处简化取首张）
            AiAnalysisResult analysis = aiService.analyzeOutfit(files.get(0).getBytes());
            System.out.println("[DEBUG] AI分析成功");
            
            // 将文件列表 URL 传回前端，方便后续发布阶段保存
            analysis.setImageUrls(urls);
            return Result.success(analysis);
        } catch (Exception e) {
            System.out.println("[DEBUG] 分析失败，异常信息: ");
            e.printStackTrace();
            return Result.fail("分析失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<String> publishOutfit(Outfit outfit) {
        try {
            System.out.println("[DEBUG] 正在发布穿搭: " + outfit.getTitle());
            // 设置默认值
            outfit.setLikeCount(0);
            outfit.setCommentCount(0);
            outfit.setFavCount(0);
            outfit.setViewCount(0);
            
            // 状态处理
            if (outfit.getStatus() == null) {
                outfit.setStatus("PUBLISHED");
            }
            
            // 如果是私有穿搭，强制 isPublic 为 false；否则为 true
            if ("PRIVATE".equalsIgnoreCase(outfit.getStatus())) {
                outfit.setIsPublic(false);
            } else {
                outfit.setIsPublic(true);
            }
            
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
                    aiMap.put("season", outfit.getSeason());
                    aiMap.put("temperatureRange", outfit.getTemperatureRange());
                    outfit.setAiAnalysis(objectMapper.writeValueAsString(aiMap));
                } catch (Exception e) {
                    System.out.println("[WARNING] 序列化 AI 分析结果失败: " + e.getMessage());
                }
            } else {
                // 如果实体类中的字段为空，但 aiAnalysis JSON 中有数据，则反向提取填充
                try {
                    com.fasterxml.jackson.databind.JsonNode aiNode = objectMapper.readTree(outfit.getAiAnalysis());
                    if ((outfit.getDescription() == null || outfit.getDescription().isEmpty() || "null".equalsIgnoreCase(outfit.getDescription())) && aiNode.has("suggestion")) {
                        outfit.setDescription(aiNode.get("suggestion").asText());
                    }
                    if ((outfit.getSeason() == null || "null".equalsIgnoreCase(outfit.getSeason())) && aiNode.has("season")) {
                        String s = aiNode.get("season").asText();
                        outfit.setSeason("null".equalsIgnoreCase(s) ? "春秋" : s);
                    }
                    if ((outfit.getTemperatureRange() == null || "null".equalsIgnoreCase(outfit.getTemperatureRange())) && aiNode.has("temperatureRange")) {
                        String t = aiNode.get("temperatureRange").asText();
                        outfit.setTemperatureRange("null".equalsIgnoreCase(t) ? "舒适" : t);
                    }
                } catch (Exception e) {
                    System.out.println("[WARNING] 反向提取信息失败: " + e.getMessage());
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

            System.out.println("[DEBUG] 发布成功，ID: " + outfit.getId());
            return Result.success("发布成功");
        } catch (Exception e) {
            System.err.println("[ERROR] 发布失败，异常: " + e.getMessage());
            e.printStackTrace();
            return Result.fail("发布失败，数据库保存异常：" + e.getMessage());
        }
    }

    @Override
    public IPage<Outfit> getMyOutfits(Long userId, int page, int size) {
        IPage<Outfit> resultPage = page(new Page<>(page, size), new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getUserId, userId)
                .eq(Outfit::getStatus, "PUBLISHED")
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
            e.printStackTrace();
            return Result.fail("帖子删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<String> incrementViewCount(Long id) {
        try {
            // 检查帖子是否存在
            Outfit outfit = getById(id);
            if (outfit == null) {
                return Result.fail("帖子不存在");
            }

            // 增加浏览计数
            update(new LambdaUpdateWrapper<Outfit>()
                    .eq(Outfit::getId, id)
                    .setSql("view_count = view_count + 1"));

            return Result.success("浏览计数增加成功");
        } catch (Exception e) {
            System.err.println("[ERROR] 增加浏览量失败: " + e.getMessage());
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
            System.err.println("[WARNING] 刷新穿搭图片URL失败: " + e.getMessage());
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

    @Override
    public List<com.campus.outfit.vo.OutfitVO> getMyPrivateOutfits(Long userId) {
        LambdaQueryWrapper<Outfit> wrapper = new LambdaQueryWrapper<Outfit>()
                .eq(Outfit::getUserId, userId)
                .eq(Outfit::getStatus, "PRIVATE")
                .orderByDesc(Outfit::getCreateTime);
        
        List<Outfit> outfits = list(wrapper);
        List<com.campus.outfit.vo.OutfitVO> vos = new ArrayList<>();
        
        for (Outfit outfit : outfits) {
            refreshOutfitUrls(outfit);
            vos.add(com.campus.outfit.vo.OutfitVO.fromOutfit(outfit, null));
        }
        return vos;
    }
    
    @Override
    @Transactional
    public void updateOutfitStatus(Long outfitId, Long userId, String status) {
        Outfit outfit = getById(outfitId);
        if (outfit == null) {
            throw new BusinessException("穿搭不存在");
        }
        if (!outfit.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该穿搭");
        }
        
        outfit.setStatus(status);
        // 同步更新公开状态：如果设为私密，则 isPublic 为 false
        if ("PRIVATE".equalsIgnoreCase(status)) {
            outfit.setIsPublic(false);
        } else if ("PUBLISHED".equalsIgnoreCase(status)) {
            outfit.setIsPublic(true);
        }
        
        updateById(outfit);
    }

    @Override
    public List<com.campus.outfit.vo.OutfitVO> getMyFavoriteOutfits(Long userId) {
        LambdaQueryWrapper<com.campus.outfit.entity.Favorite> favWrapper = new LambdaQueryWrapper<com.campus.outfit.entity.Favorite>()
                .eq(com.campus.outfit.entity.Favorite::getUserId, userId)
                .orderByDesc(com.campus.outfit.entity.Favorite::getCreateTime);
        
        List<com.campus.outfit.entity.Favorite> favorites = favoriteService.list(favWrapper);
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Long> outfitIds = favorites.stream()
                .map(com.campus.outfit.entity.Favorite::getOutfitId)
                .collect(java.util.stream.Collectors.toList());
                
        LambdaQueryWrapper<Outfit> outfitWrapper = new LambdaQueryWrapper<Outfit>()
                .in(Outfit::getId, outfitIds)
                .and(w -> w.eq(Outfit::getStatus, "PUBLISHED")
                           .or()
                           .eq(Outfit::getUserId, userId));

        List<Outfit> outfits = list(outfitWrapper);
        
        java.util.Map<Long, Outfit> outfitMap = outfits.stream().collect(java.util.stream.Collectors.toMap(Outfit::getId, o -> o));
        
        List<com.campus.outfit.vo.OutfitVO> vos = new ArrayList<>();
        for (com.campus.outfit.entity.Favorite favorite : favorites) {
            Outfit outfit = outfitMap.get(favorite.getOutfitId());
            if (outfit != null) {
                refreshOutfitUrls(outfit);
                populateLikedAndFavorited(outfit, userId);
                vos.add(com.campus.outfit.vo.OutfitVO.fromOutfit(outfit, null));
            }
        }
        
        return vos;
    }
}
