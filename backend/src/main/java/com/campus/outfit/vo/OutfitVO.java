package com.campus.outfit.vo;

import com.campus.outfit.entity.Outfit;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 穿搭视图对象，包含推荐理由
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutfitVO extends Outfit {
    private String recommendReason;
    private Integer matchScore;
    private java.util.List<String> matchLabels;
    
    public static OutfitVO fromOutfit(Outfit outfit, String reason) {
        OutfitVO vo = new OutfitVO();
        // 这里可以使用 BeanUtils.copyProperties(outfit, vo) 
        // 但为了避免引入额外依赖或复杂的反射，我们简单包装一下
        // 或者直接在 Service 层通过属性拷贝实现
        return vo;
    }
}
