package com.campus.outfit.controller;

import com.campus.outfit.entity.Comment;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.InteractionService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/like/{outfitId}")
    public Result<String> like(@PathVariable Long outfitId, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return interactionService.like(userId, outfitId);
    }

    @DeleteMapping("/like/{outfitId}")
    public Result<String> unlike(@PathVariable Long outfitId, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return interactionService.unlike(userId, outfitId);
    }

    @PostMapping("/favorite/{outfitId}")
    public Result<String> favorite(@PathVariable Long outfitId, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return interactionService.favorite(userId, outfitId);
    }

    @DeleteMapping("/favorite/{outfitId}")
    public Result<String> unfavorite(@PathVariable Long outfitId, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return interactionService.unfavorite(userId, outfitId);
    }

    @PostMapping("/comment")
    public Result<String> addComment(@RequestBody Comment comment, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        comment.setUserId(userId);
        return interactionService.addComment(comment);
    }

    @DeleteMapping("/comment/{id}")
    public Result<String> deleteComment(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return interactionService.deleteComment(id, userId);
    }

    @GetMapping("/comment/outfit/{outfitId}")
    public Result<List<Comment>> getComments(@PathVariable Long outfitId) {
        return Result.success(interactionService.getCommentsByOutfit(outfitId));
    }
}
