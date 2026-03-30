package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiChatRequest;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.AiAssistantService;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.SeedreamUtil;
import com.campus.outfit.vo.AiOutfitRecommendVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.*;
import java.net.URI;

@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    @Autowired
    private DoubaoUtil doubaoUtil;

    @Autowired
    private SeedreamUtil seedreamUtil;

    @Autowired
    private WardrobeItemMapper wardrobeItemMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String analyze(String mainBase64, String sessionId, Long userId) {
        log.info("[AiAssistant] 接收到分析请求, userId: {}, sessionId: {}", userId, sessionId);
        
        List<WardrobeItem> items = wardrobeItemMapper.selectList(
                new LambdaQueryWrapper<WardrobeItem>().eq(WardrobeItem::getUserId, userId)
        );
        
        List<String> contextBase64s = new ArrayList<>();
        contextBase64s.add(mainBase64);

        if (items != null) {
            for (WardrobeItem item : items) {
                if (item.getOriginalImageUrl() != null) {
                    try {
                        String b64 = downloadToBase64(item.getOriginalImageUrl());
                        if (b64 != null) contextBase64s.add(b64);
                    } catch (Exception e) {
                        log.warn("[AiAssistant] 上下文单品图加载失败: {}", item.getId());
                    }
                }
            }
        }

        String prompt = "你是一位专业的校园穿搭顾问。请根据主图和我的衣柜图片，必须且只能推荐 1 套搭配。返回 JSON 字段：style, suggestions (数组), recommendations (数组，仅含 id, title, desc)。";
        String aiJson = doubaoUtil.chat(userId, sessionId, prompt, contextBase64s);
        
        return enhanceJsonWithImage(aiJson, mainBase64, userId, items);
    }

    @Override
    public AiOutfitRecommendVO chatForOutfit(AiChatRequest request, Long userId) {
        log.info("[AiAssistant] 开始多轮穿搭聊天. userId: {}, sessionId: {}", userId, request.getSessionId());

        // 1. 构造强制 JSON 输出的 System 指令
        String systemInstruction = "你是一个校园穿搭AI助手。你必须严格且仅输出JSON格式，严禁包含任何Markdown标记或其他文字。" +
                "JSON结构必须为：{\"style\":\"...\",\"suggestions\":[\"...\"],\"recommendations\":{\"title\":\"...\",\"desc\":\"...\",\"image\":\"...\"}}";

        // 2. 调用 DoubaoUtil (工具类内部已实现 Redis 历史维护逻辑)
        String userInputWithPrompt = systemInstruction + "\n用户当前输入: " + request.getMessage();
        String aiResponse = doubaoUtil.chat(userId, request.getSessionId(), userInputWithPrompt);

        // 3. 清理结果并尝试解析
        try {
            String cleanedJson = cleanJsonString(aiResponse);
            return objectMapper.readValue(cleanedJson, AiOutfitRecommendVO.class);
        } catch (Exception e) {
            log.error("[AiAssistant] AI 响应解析失败. 原始内容: {}", aiResponse, e);
            throw new BusinessException("AI响应格式解析失败，请重试");
        }
    }

    private String cleanJsonString(String raw) {
        if (raw == null) return "{}";
        // 移除 Markdown 代码块标记 ```json ... ```
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        return cleaned;
    }

    private String enhanceJsonWithImage(String aiJson, String mainBase64, Long userId, List<WardrobeItem> wardrobeItems) {
        try {
            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(aiJson);
            JsonNode recNode = rootNode.get("recommendations");
            if (recNode == null || recNode.isMissingNode()) return aiJson;

            ArrayNode recommendations;
            if (recNode.isObject()) {
                recommendations = objectMapper.createArrayNode();
                recommendations.add(recNode);
                rootNode.set("recommendations", recommendations);
            } else if (recNode.isArray()) {
                recommendations = (ArrayNode) recNode;
            } else {
                return aiJson;
            }

            while (recommendations.size() > 1) {
                recommendations.remove(1);
            }

            if (recommendations.size() > 0) {
                ObjectNode itemNode = (ObjectNode) recommendations.get(0);
                Long itemId = itemNode.path("id").asLong();
                
                List<String> fusionBase64s = new ArrayList<>();
                fusionBase64s.add(mainBase64);
                
                wardrobeItems.stream()
                        .filter(i -> i.getId().equals(itemId))
                        .findFirst()
                        .ifPresent(i -> {
                            String b64 = downloadToBase64(i.getOriginalImageUrl());
                            if (b64 != null) {
                                log.info("[AiAssistant] 命中推荐单品 ID: {}, 加载为融合素材", itemId);
                                fusionBase64s.add(b64);
                            }
                        });

                String drawPrompt = "将这些衣服单品进行搭配，生成一套完整的全身的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                String effectUrl = seedreamUtil.generateImageFromMultipleBase64(drawPrompt, fusionBase64s);
                itemNode.put("image", effectUrl);
            }
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("[AiAssistant] JSON 增强逻辑异常: {}", e.getMessage());
            return aiJson;
        }
    }

    private String downloadToBase64(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(URI.create(imageUrl), byte[].class);
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && !contentType.getType().toLowerCase().contains("image")) {
                return null;
            }
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(response.getBody());
            }
        } catch (Exception e) {
             log.warn("[AiAssistant] 资源下载跳过: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String chat(String message, String sessionId, Long userId) {
        return doubaoUtil.chat(userId, sessionId, message);
    }
}
