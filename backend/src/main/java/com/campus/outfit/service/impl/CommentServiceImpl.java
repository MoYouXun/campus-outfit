package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.Comment;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.CommentMapper;
import com.campus.outfit.service.CommentService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private com.campus.outfit.service.UserService userService;

    @Override
    @Transactional
    public Result<String> addComment(Comment comment) {
        save(comment);
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, comment.getOutfitId())
                .setSql("comment_count = comment_count + 1"));
        return Result.success("评论成功");
    }

    @Override
    @Transactional
    public Result<String> deleteComment(Long commentId, Long userId) {
        Comment comment = getById(commentId);
        if (comment == null) return Result.fail("评论不存在");
        if (!comment.getUserId().equals(userId)) return Result.fail("无权删除");

        removeById(commentId);
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, comment.getOutfitId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));
        return Result.success("已删除");
    }

    @Override
    public List<Comment> getCommentsByOutfit(Long outfitId) {
        List<Comment> comments = list(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getOutfitId, outfitId)
                .orderByAsc(Comment::getCreateTime));
        
        for (Comment c : comments) {
            com.campus.outfit.entity.User user = userService.getById(c.getUserId());
            if (user != null) {
                c.setUsername(user.getUsername());
                c.setAvatar(user.getAvatar());
            }
            if (c.getReplyToUserId() != null) {
                com.campus.outfit.entity.User replyUser = userService.getById(c.getReplyToUserId());
                if (replyUser != null) {
                    c.setReplyToUserName(replyUser.getUsername());
                }
            }
        }
        return comments;
    }
}
