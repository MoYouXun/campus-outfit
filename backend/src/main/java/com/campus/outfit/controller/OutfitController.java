package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outfit")
public class OutfitController {

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private JwtUtils jwtUtils;

    private Long getUserId(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException("无效的凭证");
        }
        return jwtUtils.getUserIdFromToken(token.substring(7));
    }

    @PostMapping("/upload")
    public Result<AiAnalysisResult> uploadAndAnalyze(@RequestParam("files") List<MultipartFile> files) {
        return outfitService.uploadAndAnalyze(files);
    }

    @PostMapping("/publish")
    public Result<String> publish(@RequestBody Outfit outfit, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
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
        Long userId = getUserId(token);
        return Result.success(outfitService.getMyOutfits(userId, page, size));
    }

    @GetMapping("/user/{userId}")
    public Result<IPage<Outfit>> getUserOutfits(@PathVariable Long userId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.success(outfitService.getPublicOutfits(page, size, "latest", null, userId, null));
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteOutfit(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        return outfitService.deleteOutfit(id, userId);
    }

    @PostMapping("/{id}/view")
    public Result<String> incrementViewCount(@PathVariable Long id) {
        return outfitService.incrementViewCount(id);
    }
}
