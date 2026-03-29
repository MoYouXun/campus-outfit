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

    /**
     * 获取用户所有衣柜单品
     */
    java.util.List<WardrobeItem> getUserWardrobe(Long userId);

    /**
     * 根据类目获取衣柜单品
     */
    java.util.List<WardrobeItem> getWardrobeByType(Long userId, String type);

    /**
     * 根据季节获取衣柜单品
     */
    java.util.List<WardrobeItem> getWardrobeBySeason(Long userId, String season);

    /**
     * 根据风格获取衣柜单品
     */
    java.util.List<WardrobeItem> getWardrobeByStyle(Long userId, String style);

    /**
     * 删除衣柜单品（带权校验）
     */
    boolean deleteWardrobeItem(Long id, Long userId);
}
