package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.service.WardrobeItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 衣柜单品 Service 实现类
 */
@Slf4j
@Service
public class WardrobeItemServiceImpl extends ServiceImpl<WardrobeItemMapper, WardrobeItem> implements WardrobeItemService {

    @Autowired
    private MinioService minioService;

    @Override
    public WardrobeItem uploadItem(MultipartFile file, Long userId, String type, String color, String style, String season) {
        try {
            // 1. 上传图片到 MinIO
            String objectName = minioService.uploadImage(file);
            String originalImageUrl = minioService.getImageUrl(objectName);

            // 2. 创建单品信息
            WardrobeItem item = new WardrobeItem();
            item.setUserId(userId);
            item.setObjectName(objectName);
            item.setOriginalImageUrl(originalImageUrl);
            item.setCategoryMain(type);
            item.setColor(color);
            item.setStyle(style);
            item.setSeason(season);
            
            // 模拟原始 AI 标签 JSON
            String mockAiTags = String.format("{\"style\": \"%s\", \"originalType\": \"%s\"}", style, type);
            item.setAiRawTags(mockAiTags);

            // 3. 持久化到数据库
            this.save(item);

            return item;
        } catch (Exception e) {
            log.error("单品上传失败", e);
            throw new RuntimeException("衣柜单品处理异常：" + e.getMessage());
        }
    }

    @Override
    public List<WardrobeItem> getByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                .orderByDesc(WardrobeItem::getCreateTime));
    }

    @Override
    public List<WardrobeItem> getByType(Long userId, String type) {
        return this.list(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                .eq(WardrobeItem::getCategoryMain, type)
                .orderByDesc(WardrobeItem::getCreateTime));
    }

    @Override
    public List<WardrobeItem> getBySeason(Long userId, String season) {
        return this.list(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                .eq(WardrobeItem::getSeason, season)
                .orderByDesc(WardrobeItem::getCreateTime));
    }

    @Override
    public List<WardrobeItem> getByStyle(Long userId, String style) {
        return this.list(new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId)
                .eq(WardrobeItem::getStyle, style)
                .orderByDesc(WardrobeItem::getCreateTime));
    }

    @Override
    public boolean deleteItem(Long id) {
        WardrobeItem item = this.getById(id);
        if (item == null) {
            return false;
        }

        // 1. 从 MinIO 删除文件
        if (item.getObjectName() != null) {
            try {
                minioService.deleteImage(item.getObjectName());
            } catch (Exception e) {
                log.error("从 MinIO 删除文件失败, id: {}, objectName: {}", id, item.getObjectName(), e);
                // 即使 MinIO 失败，通常也继续删除数据库记录，或者根据业务决定
            }
        }

        // 2. 从数据库逻辑删除
        return this.removeById(id);
    }
}
