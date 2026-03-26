package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.RecommendService;
import com.campus.outfit.utils.Result;
import com.campus.outfit.vo.OutfitVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/season")
    public Result<IPage<OutfitVO>> recommendBySeason(
            @RequestParam(required = false, defaultValue = "北京") String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(defaultValue = "10") int size) {
        try {
            return Result.success(recommendService.recommendBySeason(city, latitude, longitude, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
        }
    }

    @GetMapping("/occasion")
    public Result<IPage<OutfitVO>> recommendByOccasion(
            @RequestParam String occasion, 
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(defaultValue = "10") int size) {
        try {
            return Result.success(recommendService.recommendByOccasion(occasion, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
        }
    }

    @GetMapping("/style")
    public Result<IPage<OutfitVO>> recommendByStyle(
            @RequestHeader("Authorization") String token, 
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(defaultValue = "10") int size) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            return Result.success(recommendService.recommendByStyle(userId, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
        }
    }

    @GetMapping("/personal")
    public Result<IPage<OutfitVO>> recommendPersonalized(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false, defaultValue = "北京") String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String scenario,
            @RequestParam(defaultValue = "1") int page, 
            @RequestParam(defaultValue = "10") int size) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            return Result.success(recommendService.recommendPersonalized(userId, city, latitude, longitude, scenario, page, size));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
        }
    }
}
