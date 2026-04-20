package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.service.WardrobeItemService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

/**
 * 衣柜单品 Service 实现类
 */
@Slf4j
@Service
public class WardrobeItemServiceImpl extends ServiceImpl<WardrobeItemMapper, WardrobeItem> implements WardrobeItemService {

    @Autowired
    private MinioService minioService;

    @Autowired
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WardrobeItem uploadAndAnalyzeItem(MultipartFile file, Long userId) {
        String objectName = null;
        try {
            // 1. 上传图片到 MinIO
            objectName = minioService.uploadImage(file);
            String originalImageUrl = minioService.getImageUrl(objectName);

            // 2. 将上传文件转为 Base64 以供 AI 视觉模型分析
            byte[] fileBytes = file.getBytes();
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);
            String contentType = file.getContentType();
            String dataUri = "data:" + (contentType != null ? contentType : "image/jpeg") + ";base64," + base64Content;

            // 3. 调用 AI 视觉服务进行单品鉴定与属性分析
            log.info("[Wardrobe] 正在对用户 {} 上传的单品进行 AI 鉴定...", userId);
            String aiJson = aiService.analyzeWardrobeItem(dataUri);
            JsonNode rootNode = objectMapper.readTree(aiJson);

            // 4. 核心逻辑拦截：判定是否为有效单品
            boolean isSingleItem = rootNode.path("isSingleItem").asBoolean();
            if (!isSingleItem) {
                String reason = rootNode.path("reason").asText("识别到非单品内容（如穿搭照或杂乱背景）");
                log.warn("[Wardrobe] AI 鉴定未通过，原因: {}, 正在执行存储回滚...", reason);
                // 物理删除已上传的冗余文件
                minioService.removeObject(objectName);
                throw new BusinessException("上传失败：" + reason);
            }

            // 5. 校验通过，提取结构化属性并持久化
            WardrobeItem item = new WardrobeItem();
            item.setUserId(userId);
            item.setOriginalImageUrl(originalImageUrl);
            
            // 从 AI JSON 中提取属性映射到实体类字段
            item.setCategoryMain(rootNode.path("categoryMain").asText("其他"));
            item.setCategorySub(rootNode.path("categorySub").asText("未知品类"));
            item.setColor(rootNode.path("color").asText("未知颜色"));
            item.setMaterial(rootNode.path("material").asText("未知材质"));
            item.setSeason(rootNode.path("season").asText("全季节"));
            item.setAiRawTags(aiJson);

            log.info("[Wardrobe] AI 鉴定通过，识别为：{} - {} - {}", item.getCategoryMain(), item.getCategorySub(), item.getColor());
            
            // 6. 持久化到数据库
            this.save(item);

            return item;
        } catch (BusinessException e) {
            // 业务异常直接透传，由全局异常处理器捕获
            throw e;
        } catch (Exception e) {
            log.error("单品上传与 AI 分析流程异常", e);
            // 出现非业务预期的系统异常时进行包装
            throw new RuntimeException("系统处理单品异常：" + e.getMessage());
        }
    }

    @Override
    public List<WardrobeItem> getUserWardrobe(Long userId) {
        QueryWrapper<WardrobeItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        List<WardrobeItem> list = this.list(queryWrapper);
        list.forEach(this::refreshImageUrl);
        return list;
    }

    @Override
    public List<WardrobeItem> getWardrobeByType(Long userId, String type) {
        QueryWrapper<WardrobeItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("category_main", type);
        List<WardrobeItem> list = this.list(queryWrapper);
        list.forEach(this::refreshImageUrl);
        return list;
    }

    @Override
    public List<WardrobeItem> getWardrobeBySeason(Long userId, String season) {
        QueryWrapper<WardrobeItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).like("season", season);
        List<WardrobeItem> list = this.list(queryWrapper);
        list.forEach(this::refreshImageUrl);
        return list;
    }

    @Override
    public List<WardrobeItem> getWardrobeByStyle(Long userId, String style) {
        QueryWrapper<WardrobeItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).like("ai_raw_tags", style);
        List<WardrobeItem> list = this.list(queryWrapper);
        list.forEach(this::refreshImageUrl);
        return list;
    }

    @Override
    public List<WardrobeItem> uploadBatch(MultipartFile[] files, Long userId) {
        log.info("[Wardrobe] 用户 {} 发起批量上传单品任务，总计: {} 张", userId, files.length);
        List<WardrobeItem> results = new java.util.ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                // 复用单张上传的逻辑，包含存储、AI 分析、审核和持久化
                WardrobeItem item = uploadAndAnalyzeItem(file, userId);
                results.add(item);
            } catch (Exception e) {
                // 核心需求：捕获单张图片的所有异常（包含 AI 鉴定拒绝、底图错误等），跳过并继续
                log.warn("[Wardrobe] 批量任务中单张图片处理失败, 忽略并继续: {}, 原因: {}", 
                        file.getOriginalFilename(), e.getMessage());
            }
        }
        
        log.info("[Wardrobe] 批量处理完成，成功上传: {}/{}", results.size(), files.length);
        return results;
    }

    @Override
    public boolean deleteWardrobeItem(Long id, Long userId) {
        // 先查询单品，验证所属权
        WardrobeItem item = this.getById(id);
        if (item == null || !item.getUserId().equals(userId)) {
            return false;
        }
        return this.removeById(id);
    }

    @Override
    public void refreshImageUrl(WardrobeItem item) {
        if (item == null) return;
        try {
            if (item.getOriginalImageUrl() != null) {
                String objName = minioService.extractObjectName(item.getOriginalImageUrl());
                if (objName != null) {
                    item.setOriginalImageUrl(minioService.getImageUrl(objName));
                }
            }
            if (item.getProcessedImageUrl() != null) {
                String objName = minioService.extractObjectName(item.getProcessedImageUrl());
                if (objName != null) {
                    item.setProcessedImageUrl(minioService.getImageUrl(objName));
                }
            }
        } catch (Exception e) {
            log.warn("[Wardrobe] 刷新单品图片 URL 失败, itemId: {}, reason: {}", item.getId(), e.getMessage());
        }
    }
}
