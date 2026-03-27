package com.campus.outfit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.outfit.entity.WardrobeItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 衣柜单品 Mapper 接口
 */
@Mapper
public interface WardrobeItemMapper extends BaseMapper<WardrobeItem> {
}
