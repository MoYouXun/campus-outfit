package com.campus.outfit.controller;

import com.campus.outfit.entity.Comment;
import com.campus.outfit.service.CommentService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @PostMapping
    public Result<String> addComment(@RequestBody Comment comment) {
        return commentService.addComment(comment);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteComment(@PathVariable Long id, @RequestParam Long userId) {
        return commentService.deleteComment(id, userId);
    }

    @GetMapping("/outfit/{outfitId}")
    public Result<List<Comment>> getComments(@PathVariable Long outfitId) {
        return Result.success(commentService.getCommentsByOutfit(outfitId));
    }
}
