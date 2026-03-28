package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.service.WardrobeItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 衣柜单品 Service 实现类
 */
@Slf4j
@Service
public class WardrobeItemServiceImpl extends ServiceImpl<WardrobeItemMapper, WardrobeItem> implements WardrobeItemService {

    @Autowired
    private MinioService minioService;

    @Override
    public WardrobeItem uploadAndAnalyzeItem(MultipartFile file, Long userId) {
        try {
            // 1. 上传图片到 MinIO
            String objectName = minioService.uploadImage(file);
            String originalImageUrl = minioService.getImageUrl(objectName);

            // 2. 创建 Mock AI 识别结果
            WardrobeItem item = new WardrobeItem();
            item.setUserId(userId);
            item.setOriginalImageUrl(originalImageUrl);
            
            // 模拟 AI 识别数据
            item.setCategoryMain("上装");
            item.setCategorySub("卫衣");
            item.setColor("黑色");
            item.setMaterial("纯棉");
            item.setSeason("秋,冬");
            
            // 模拟原始 AI 标签 JSON
            String mockAiTags = "{\"style\": \"休闲\", \"elements\": [\"连帽\", \"宽松\"], \"accuracy\": 0.98}";
            item.setAiRawTags(mockAiTags);

            // 3. 持久化到数据库
            this.save(item);

            return item;
        } catch (Exception e) {
            log.error("单品上传与 AI 分析失败", e);
            throw new RuntimeException("衣柜单品处理异常：" + e.getMessage());
        }
    }
}
