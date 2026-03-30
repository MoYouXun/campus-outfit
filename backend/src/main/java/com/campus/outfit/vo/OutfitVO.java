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
    private String username;
    private String userAvatar;
    private String status;
    
    public static OutfitVO fromOutfit(Outfit outfit, String reason) {
        OutfitVO vo = new OutfitVO();
        org.springframework.beans.BeanUtils.copyProperties(outfit, vo);
        vo.setRecommendReason(reason);
        return vo;
    }
}
