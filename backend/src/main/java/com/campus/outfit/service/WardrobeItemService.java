package com.campus.outfit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.WardrobeItem;
import org.springframework.web.multipart.MultipartFile;

/**
 * 衣柜单品 Service 接口
 */
public interface WardrobeItemService extends IService<WardrobeItem> {
    /**
     * 上传单品并进行 AI 分析（Mock 逻辑）
     * @param file 单品图片
     * @param userId 所属用户ID
     * @return 解析并保存后的单品对象
     */
    WardrobeItem uploadAndAnalyzeItem(MultipartFile file, Long userId);
}
