package com.campus.outfit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.WardrobeItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 衣柜单品 Service 接口
 */
public interface WardrobeItemService extends IService<WardrobeItem> {
    /**
     * 上传单品并记录元数据
     * @param file 单品图片
     * @param userId 所属用户ID
     * @param type 类型
     * @param color 颜色
     * @param style 风格
     * @param season 季节
     * @return 解析并保存后的单品对象
     */
    WardrobeItem uploadItem(MultipartFile file, Long userId, String type, String color, String style, String season);

    /**
     * 根据用户获取所有衣柜单品
     */
    List<WardrobeItem> getByUserId(Long userId);

    /**
     * 根据类型筛选
     */
    List<WardrobeItem> getByType(Long userId, String type);

    /**
     * 根据季节筛选
     */
    List<WardrobeItem> getBySeason(Long userId, String season);

    /**
     * 根据风格筛选
     */
    List<WardrobeItem> getByStyle(Long userId, String style);

    /**
     * 删除单品记录及对应的文件
     */
    boolean deleteItem(Long id);
}
