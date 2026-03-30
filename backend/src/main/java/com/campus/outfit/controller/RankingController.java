package com.campus.outfit.controller;

import com.campus.outfit.vo.OutfitVO;
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
    public Result<List<OutfitVO>> getHotRanking(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getHotRanking(limit));
    }

    @GetMapping("/style")
    public Result<List<OutfitVO>> getStyleRanking(@RequestParam String style, @RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getStyleRanking(style, limit));
    }

    @GetMapping("/school")
    public Result<List<OutfitVO>> getSchoolRanking(@RequestParam String school, @RequestParam(defaultValue = "10") int limit) {
        return Result.success(rankingService.getSchoolRanking(school, limit));
    }

    @PostMapping("/refresh")
    public Result<String> refresh() {
        rankingService.refreshRankings();
        return Result.success("手动刷新成功");
    }
}
