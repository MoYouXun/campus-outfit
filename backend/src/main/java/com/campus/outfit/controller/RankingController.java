package com.campus.outfit.controller;

import com.campus.outfit.entity.Outfit;
import com.campus.outfit.service.RankingService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    @Autowired
    private RankingService rankingService;

    @GetMapping("/hot")
    public Result<List<Outfit>> getHotRanking(@RequestParam(required = false) String gender, @RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getHotRanking(gender, limit));
    }

    @GetMapping("/style")
    public Result<List<Outfit>> getStyleRanking(@RequestParam String style, @RequestParam(required = false) String gender, @RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getStyleRanking(style, gender, limit));
    }

    @GetMapping("/school")
    public Result<List<Outfit>> getSchoolRanking(@RequestParam String school, @RequestParam(required = false) String gender, @RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getSchoolRanking(school, gender, limit));
    }

    @PostMapping("/refresh")
    public Result<String> refresh() {
        rankingService.refreshRankings();
        return Result.success("手动刷新成功");
    }
}
