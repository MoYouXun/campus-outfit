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

import java.util.List;

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

    /**
     * AI 分析请求参数
     */
    @Data
    public static class AiAnalyzeRequest {
        private String base64Image;
        private String sessionId;
    }

    /**
     * AI 聊天请求参数
     */
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
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
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
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
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
            e.printStackTrace();
            return Result.fail("获取推荐失败，请稍后重试");
        }
    }

    @PostMapping("/ai-analyze")
    public Result<String> aiAnalyze(@RequestBody AiAnalyzeRequest request, 
                                    @RequestHeader("Authorization") String token) {
        log.info("[AI Recommend] 收到穿搭分析请求，sessionId: {}", request.getSessionId());
        try {
            // 1. 获取用户信息与衣柜数据
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            List<WardrobeItem> wardrobeItems = wardrobeItemService.getUserWardrobe(userId);

            // 2. 调用 AI 基础分析逻辑（返回 JSON 字符串）
            String aiJsonResult = aiService.analyzeOutfitWithWardrobe(
                    request.getBase64Image(), userId, request.getSessionId(), wardrobeItems);

            // 3. 使用 Jackson 解析并加工 JSON，为推荐单品生成预览效果图
            JsonNode rootNode = objectMapper.readTree(aiJsonResult);
            if (rootNode.has("recommendations") && rootNode.get("recommendations").isArray()) {
                ArrayNode recommendations = (ArrayNode) rootNode.get("recommendations");
                for (JsonNode node : recommendations) {
                    if (node instanceof ObjectNode) {
                        ObjectNode itemNode = (ObjectNode) node;
                        Long itemId = itemNode.path("id").asLong();
                        String reason = itemNode.path("reason").asText();

                        // 查找对应的衣柜单品详情，用于生成更精准的 Prompt
                        WardrobeItem detail = wardrobeItems.stream()
                                .filter(i -> i.getId().equals(itemId))
                                .findFirst().orElse(null);

                        String prompt;
                        if (detail != null) {
                            prompt = String.format("一张精美的校园穿搭效果图，模特穿着： %s 颜色的 %s 材质 %s，整体风格： %s，建议理由： %s",
                                    detail.getColor(), detail.getMaterial(), detail.getCategorySub(), 
                                    rootNode.path("style").asText(), reason);
                        } else {
                            prompt = "一张精美的穿搭效果图，搭配推荐理由：" + reason;
                        }

                        // 调用 AI 绘图接口生成 2K 效果图
                        try {
                            log.info("[AI Recommend] 正在为单品 ID: {} 生成预览效果图...", itemId);
                            String effectUrl = aiService.generateImage(prompt);
                            itemNode.put("effectUrl", effectUrl);
                        } catch (Exception e) {
                            log.error("[AI Recommend] 单品效果图生成失败: {}", e.getMessage());
                            itemNode.put("effectUrl", ""); // 生成失败留空
                        }
                    }
                }
            }

            // 4. 返回加工后的结果
            return Result.success(objectMapper.writeValueAsString(rootNode));

        } catch (Exception e) {
            log.error("[AI Recommend] 穿搭分析业务流异常", e);
            return Result.fail("智能穿搭分析服务暂时不可用：" + e.getMessage());
        }
    }

    @PostMapping("/ai-chat")
    public Result<String> aiChat(@RequestBody AiChatRequest request,
                                 @RequestHeader("Authorization") String token) {
        log.info("[AI Recommend] 收到衣柜对话请求，sessionId: {}", request.getSessionId());
        try {
            Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
            List<WardrobeItem> wardrobeItems = wardrobeItemService.getUserWardrobe(userId);

            // 直接调用带上下文的对话服务
            String reply = aiService.chatWithWardrobeContext(
                    request.getSessionId(), request.getMessage(), wardrobeItems);
            
            return Result.success(reply);
        } catch (Exception e) {
            log.error("[AI Recommend] 智能对话异常", e);
            return Result.fail("对话服务异常，请稍后再试");
        }
    }
}
