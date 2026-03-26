package com.campus.outfit.dto;

import com.campus.outfit.entity.Comment;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.User;
import lombok.Data;

import java.util.List;

@Data
public class OutfitDetailDto {
    private Outfit outfit;
    private User author;
    private List<Comment> comments;
    
    // 互动状态
    private boolean isLiked;
    private boolean isFavorited;
    private boolean isFollowingAuthor;
}
