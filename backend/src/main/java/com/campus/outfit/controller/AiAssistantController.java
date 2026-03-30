package com.campus.outfit.controller;

import com.campus.outfit.dto.AiAnalyzeRequest;
import com.campus.outfit.dto.AiChatRequest;
import com.campus.outfit.service.AiAssistantService;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 穿搭助手控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final JwtUtils jwtUtils;

    /**
     * 分析穿搭主图
     */
    @PostMapping("/analyze")
    public Result<String> analyze(@RequestBody AiAnalyzeRequest request, HttpServletRequest httpServletRequest) {
        Long userId = getUserId(httpServletRequest);
        if (userId == null) {
            return Result.fail("用户未登录或 Token 无效");
        }
        
        log.info("[AiController] 接收到分析请求, userId: {}, sessionId: {}", userId, request.getSessionId());
        String result = aiAssistantService.analyze(request.getBase64Image(), request.getSessionId(), userId);
        return Result.success(result);
    }

    /**
     * 穿搭对话
     */
    @PostMapping("/chat")
    public Result<Object> chat(@RequestBody AiChatRequest request, HttpServletRequest httpServletRequest) {
        Long userId = getUserId(httpServletRequest);
        if (userId == null) {
            return Result.fail("用户未登录或 Token 无效");
        }

        log.info("[AiController] 接收到对话请求, userId: {}, sessionId: {}", userId, request.getSessionId());
        // 强制路由到多模态+衣柜注入+重绘的增强对话方法
        com.campus.outfit.vo.AiOutfitRecommendVO result = aiAssistantService.chatForOutfit(request, userId);
        return Result.success(result);
    }

    /**
     * 工具方法：从 Header 中解析 UserID
     */
    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            return null;
        }
        // 处理可能存在的 "Bearer " 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("[AiController] Token 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
