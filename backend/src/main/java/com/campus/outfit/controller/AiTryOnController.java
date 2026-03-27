package com.campus.outfit.controller;

import com.campus.outfit.dto.AiTryOnRequest;
import com.campus.outfit.dto.AiTryOnResponse;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.AiTryOnService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 试衣间 API 控制器
 */
@RestController
@RequestMapping("/api/ai/try-on")
public class AiTryOnController {

    @Autowired
    private AiTryOnService aiTryOnService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 发起 AI 试衣生成请求
     */
    @PostMapping("/generate")
    public Result<AiTryOnResponse> generate(@RequestBody AiTryOnRequest request, 
                                           @RequestHeader("Authorization") String token) {
        // 从 Token 中解析用户 ID
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        
        // 执行生成逻辑
        AiTryOnResponse response = aiTryOnService.generate(request, userId);
        
        return Result.success(response);
    }
}
