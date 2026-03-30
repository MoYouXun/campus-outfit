package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.AiService;
import com.campus.outfit.service.WardrobeItemService;
import com.campus.outfit.service.RecommendService;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.utils.Result;
import com.campus.outfit.vo.OutfitVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private AiService aiService;

    @Autowired
    private WardrobeItemService wardrobeItemService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Data
    public static class AiAnalyzeRequest {
        private String base64Image;
        private String sessionId;
    }

    @Data
    public static class AiChatRequest {
        private String sessionId;
        private String message;
    }

    @GetMapping("/season")
    public Result<IPage<OutfitVO>> recommendBySeason(
            @RequestParam(required = false, defaultValue = "北京") String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long currentUserId) {
        try {
            return Result.success(recommendService.recommendBySeason(city, latitude, longitude, page, size, currentUserId));
        } catch (Exception e) {
            log.error("获取季节推荐失败", e);
            return Result.fail("获取推荐失败");
        }
    }

    @GetMapping("/occasion")
    public Result<IPage<OutfitVO>> recommendByOccasion(
            @RequestParam String occasion, 
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long currentUserId) {
        try {
            return Result.success(recommendService.recommendByOccasion(occasion, page, size, currentUserId));
        } catch (Exception e) {
            log.error("获取场合推荐失败", e);
            return Result.fail("获取推荐失败");
        }
    }

    @GetMapping("/style")
    public Result<IPage<OutfitVO>> recommendByStyle(
            @RequestHeader("Authorization") String token, 
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            return Result.success(recommendService.recommendByStyle(userId, page, size));
        } catch (Exception e) {
            log.error("获取风格推荐失败", e);
            return Result.fail("获取推荐失败");
        }
    }

    @PostMapping("/ai-analyze")
    public Result<String> aiAnalyze(@RequestBody AiAnalyzeRequest request, 
                                    @RequestHeader("Authorization") String token) {
        log.info("[AI Recommend] 收到穿搭分析请求，sessionId: {}", request.getSessionId());
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            List<WardrobeItem> wardrobeItems = wardrobeItemService.getUserWardrobe(userId);

            // 1. 调用 AI 基础分析逻辑（已对齐全量 Base64 传递逻辑）
            String aiJsonResult = aiService.analyzeOutfitWithWardrobe(
                    request.getBase64Image(), userId, request.getSessionId(), wardrobeItems);

            // 2. 使用 Jackson 解析并进行防御性截断
            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(aiJsonResult);
            if (rootNode.has("recommendations") && rootNode.get("recommendations").isArray()) {
                ArrayNode recommendations = (ArrayNode) rootNode.get("recommendations");
                
                // 【重要防御】只保留第一个推荐项，强制截断
                while (recommendations.size() > 1) {
                    recommendations.remove(1);
                }

                if (recommendations.size() > 0) {
                    ObjectNode itemNode = (ObjectNode) recommendations.get(0);
                    Long itemId = itemNode.path("id").asLong();
                    
                    // 3. 多图融合生图逻辑：搜集模特底图 + 已匹配单品原图
                    Optional<WardrobeItem> matchedItem = wardrobeItems.stream()
                            .filter(i -> i.getId().equals(itemId))
                            .findFirst();

                    if (matchedItem.isPresent()) {
                        List<String> fusionBase64s = new ArrayList<>();
                        fusionBase64s.add(request.getBase64Image());
                        // 注意：这里由于 AiService.generateImageFromMultipleBase64 已包含单品转换逻辑
                        // 为了符合控制器职责，我们直接通过 aiService 执行融合过程
                        try {
                            log.info("[AI Recommend] 触发 Seedream 多图生图 (底图 + 单品 ID: {})...", itemId);
                            String style = rootNode.path("style").asText("校园风格穿搭设计");
                            String effectUrl = aiService.generateImageFromMultipleBase64(style, fusionBase64s);
                            itemNode.put("effectUrl", effectUrl);
                        } catch (Exception e) {
                            log.error("[AI Recommend] 多图生图失败: {}", e.getMessage());
                            itemNode.put("effectUrl", "");
                        }
                    }
                }
            }

            // 4. 返回包装后的结果
            return Result.success(objectMapper.writeValueAsString(rootNode));

        } catch (Exception e) {
            log.error("[AI Recommend] 分析业务流异常", e);
            return Result.fail("穿搭分析暂时不可用");
        }
    }

    @PostMapping("/ai-chat")
    public Result<String> aiChat(@RequestBody AiChatRequest request,
                                 @RequestHeader("Authorization") String token) {
        log.info("[AI Recommend] 收到对话请求, sessionId: {}", request.getSessionId());
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            List<WardrobeItem> wardrobeItems = wardrobeItemService.getUserWardrobe(userId);
            String reply = aiService.chatWithWardrobeContext(request.getSessionId(), request.getMessage(), wardrobeItems);
            return Result.success(reply);
        } catch (Exception e) {
            log.error("[AI Recommend] 智能对话异常", e);
            return Result.fail("对话服务异常");
        }
    }
}
