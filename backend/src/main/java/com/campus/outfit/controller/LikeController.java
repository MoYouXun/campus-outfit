package com.campus.outfit.controller;

import com.campus.outfit.service.LikeService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/like")
public class LikeController {

    @Autowired
    private LikeService likeService;

    @PostMapping("/{outfitId}")
    public Result<String> like(@PathVariable Long outfitId, @RequestParam Long userId) {
        return likeService.like(userId, outfitId);
    }

    @DeleteMapping("/{outfitId}")
    public Result<String> unlike(@PathVariable Long outfitId, @RequestParam Long userId) {
        return likeService.unlike(userId, outfitId);
    }
}
