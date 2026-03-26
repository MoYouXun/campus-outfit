package com.campus.outfit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.Comment;
import com.campus.outfit.utils.Result;

import java.util.List;

public interface CommentService extends IService<Comment> {
    Result<String> addComment(Comment comment);
    Result<String> deleteComment(Long commentId, Long userId);
    List<Comment> getCommentsByOutfit(Long outfitId);
}
