package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Comment;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.utils.Result;

import java.util.List;

public interface InteractionService {
    Result<String> like(Long userId, Long outfitId);
    Result<String> unlike(Long userId, Long outfitId);
    
    Result<String> favorite(Long userId, Long outfitId);
    Result<String> unfavorite(Long userId, Long outfitId);
    IPage<Favorite> getMyFavorites(Long userId, int page, int size);

    Result<String> addComment(Comment comment);
    Result<String> deleteComment(Long commentId, Long userId);
    List<Comment> getCommentsByOutfit(Long outfitId);
}
