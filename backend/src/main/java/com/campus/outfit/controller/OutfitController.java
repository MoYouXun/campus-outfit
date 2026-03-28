package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/outfit")
public class OutfitController {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/upload")
    public Result<AiAnalysisResult> uploadAndAnalyze(@RequestParam("files") List<MultipartFile> files) {
        return outfitService.uploadAndAnalyze(files);
    }

    @PostMapping("/publish")
    public Result<String> publish(@RequestBody Outfit outfit, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        outfit.setUserId(userId);
        return outfitService.publishOutfit(outfit);
    }

    @GetMapping("/{id}")
    public Result<Outfit> getDetail(@PathVariable Long id) {
        return Result.success(outfitService.getById(id));
    }

    @GetMapping("/mine")
    public Result<IPage<Outfit>> getMyOutfits(@RequestHeader("Authorization") String token, 
                                             @RequestParam(defaultValue = "1") int page, 
                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(outfitService.getMyOutfits(userId, page, size));
    }

    @GetMapping("/my-private")
    public Result<List<com.campus.outfit.vo.OutfitVO>> getMyPrivateOutfits(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(outfitService.getMyPrivateOutfits(userId));
    }

    @GetMapping("/user/{userId}")
    public Result<IPage<Outfit>> getUserOutfits(@PathVariable Long userId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.success(outfitService.getPublicOutfits(page, size, "latest", null, userId, null));
    }



    @DeleteMapping("/{id}")
    public Result<String> deleteOutfit(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return outfitService.deleteOutfit(id, userId);
    }

    @PostMapping("/{id}/view")
    public Result<String> incrementViewCount(@PathVariable Long id) {
        return outfitService.incrementViewCount(id);
    }
}
