package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.entity.Favorite;
import com.campus.outfit.service.FavoriteService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping("/{outfitId}")
    public Result<String> favorite(@PathVariable Long outfitId, @RequestParam Long userId) {
        return favoriteService.favorite(userId, outfitId);
    }

    @DeleteMapping("/{outfitId}")
    public Result<String> unfavorite(@PathVariable Long outfitId, @RequestParam Long userId) {
        return favoriteService.unfavorite(userId, outfitId);
    }

    @GetMapping("/mine")
    public Result<IPage<Favorite>> getMyFavorites(@RequestParam Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return Result.success(favoriteService.getMyFavorites(userId, page, size));
    }
}
