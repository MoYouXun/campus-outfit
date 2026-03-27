package com.campus.outfit.controller;

import com.campus.outfit.dto.OutfitPkRequest;
import com.campus.outfit.dto.OutfitPkResponse;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.AiDecisionService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 决策 (PK/对比) API 控制器
 */
@RestController
@RequestMapping("/api/ai/decision")
public class AiDecisionController {

    @Autowired
    private AiDecisionService aiDecisionService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 两套穿搭 A/B PK
     */
    @PostMapping("/pk")
    public Result<OutfitPkResponse> pk(@RequestBody OutfitPkRequest request, 
                                       @RequestHeader("Authorization") String token) {
        // 解析当前登录用户 ID
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        
        // 调用 AI 决策服务
        OutfitPkResponse response = aiDecisionService.pkOutfits(request, userId);
        
        return Result.success(response);
    }
}
