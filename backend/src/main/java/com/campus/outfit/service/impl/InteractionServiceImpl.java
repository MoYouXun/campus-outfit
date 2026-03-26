package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.outfit.entity.Comment;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.mapper.CommentMapper;
import com.campus.outfit.mapper.FavoriteMapper;
import com.campus.outfit.service.InteractionService;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InteractionServiceImpl implements InteractionService {

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private OutfitService outfitService;

    @Override
    @Transactional
    public Result<String> like(Long userId, Long outfitId) {
        // 点赞通常可以用 Redis 优化，这里暂用数据库记录或直接更新 Outfit 里的 count（暂定直接更新 count 快照，因为没有单独 Like 表）
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, outfitId)
                .setSql("like_count = like_count + 1"));
        return Result.success("点赞成功");
    }

    @Override
    @Transactional
    public Result<String> unlike(Long userId, Long outfitId) {
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, outfitId)
                .setSql("like_count = GREATEST(0, like_count - 1)"));
        return Result.success("取消点赞");
    }

    @Override
    @Transactional
    public Result<String> favorite(Long userId, Long outfitId) {
        if (favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getOutfitId, outfitId)) > 0) {
            return Result.fail("已收藏过");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setOutfitId(outfitId);
        favoriteMapper.insert(favorite);

        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, outfitId)
                .setSql("fav_count = fav_count + 1"));
        return Result.success("收藏成功");
    }

    @Override
    @Transactional
    public Result<String> unfavorite(Long userId, Long outfitId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getOutfitId, outfitId));
        if (deleted > 0) {
            outfitService.update(new LambdaUpdateWrapper<Outfit>()
                    .eq(Outfit::getId, outfitId)
                    .setSql("fav_count = GREATEST(0, fav_count - 1)"));
        }
        return Result.success("取消收藏");
    }

    @Override
    public IPage<Favorite> getMyFavorites(Long userId, int page, int size) {
        return favoriteMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));
    }

    @Override
    @Transactional
    public Result<String> addComment(Comment comment) {
        commentMapper.insert(comment);
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, comment.getOutfitId())
                .setSql("comment_count = comment_count + 1"));
        return Result.success("评论成功");
    }

    @Override
    @Transactional
    public Result<String> deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getUserId().equals(userId)) {
            return Result.fail("无权删除");
        }
        commentMapper.deleteById(commentId);
        outfitService.update(new LambdaUpdateWrapper<Outfit>()
                .eq(Outfit::getId, comment.getOutfitId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));
        return Result.success("已删除");
    }

    @Override
    public List<Comment> getCommentsByOutfit(Long outfitId) {
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getOutfitId, outfitId)
                .orderByAsc(Comment::getCreateTime));
    }
}
