package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiChatRequest;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.AiAssistantService;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.DoubaoUtil.DoubaoMessage;
import com.campus.outfit.util.DoubaoUtil.DoubaoContentPart;
import com.campus.outfit.util.SeedreamUtil;
import com.campus.outfit.vo.AiOutfitRecommendVO;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.TimeUnit;
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

    private static final String CONTEXT_KEY_PREFIX = "ai:context:";
    private static final long EXPIRE_HOURS = 2;

    @Override
    public String analyze(String mainBase64, String sessionId, Long userId) {
        log.info("[AiAssistant] 接收到分析请求, userId: {}, sessionId: {}", userId, sessionId);
        
        List<WardrobeItem> items = wardrobeItemMapper.selectList(
                new LambdaQueryWrapper<WardrobeItem>().eq(WardrobeItem::getUserId, userId)
        );
        
        // 构造素材列表
        List<String> rawImages = new ArrayList<>();
        rawImages.add(mainBase64);
        if (items != null) {
            for (WardrobeItem item : items) {
                if (item.getOriginalImageUrl() != null) rawImages.add(item.getOriginalImageUrl());
            }
        }

        // 构造消息列表
        List<DoubaoMessage> messages = new ArrayList<>();
        messages.add(new DoubaoMessage("system", "你是一位专业的校园穿搭顾问。请根据主图和我的衣柜图片，必须且只能推荐 1 套搭配。返回 JSON 字段：style, suggestions (数组), recommendations (数组，仅含 id, title, desc)。"));
        
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        List<DoubaoContentPart> parts = new ArrayList<>();
        parts.add(DoubaoContentPart.text("这是我的照片和衣柜，请提供分析与建议。"));
        for (String src : rawImages) {
            String fmt = doubaoUtil.resolveAndFormatImage(src);
            if (fmt != null) parts.add(DoubaoContentPart.image(fmt));
        }
        userMsg.setContent(parts);
        messages.add(userMsg);

        String aiJson = doubaoUtil.chatWithVision(messages);
        String enhancedJson = enhanceJsonWithImage(aiJson, mainBase64, userId, items);
        
        // 【新增】将第一轮对话和包含Base64的主图持久化到 Redis，防止第二轮对话上下文断裂
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;
        List<DoubaoMessage> history = new ArrayList<>(messages);
        history.add(new DoubaoMessage("assistant", enhancedJson));
        saveHistory(redisKey, history);
        
        return enhancedJson;
    }

    @Override
    public AiOutfitRecommendVO chatForOutfit(AiChatRequest request, Long userId) {
        log.info("[AiAssistant] 开始多轮穿搭对话. userId: {}, sessionId: {}", userId, request.getSessionId());

        String redisKey = CONTEXT_KEY_PREFIX + request.getSessionId();
        List<DoubaoMessage> history = loadHistory(redisKey);
        
        // 1. 召回衣柜数据准备注入上下文
        List<WardrobeItem> wardrobeItems = wardrobeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WardrobeItem>().eq(WardrobeItem::getUserId, userId)
        );

        // 2. 刷新强化 System Prompt，强制进行衣柜替换逻辑
        String systemInstruction = "你是一个支持视觉分析的AI穿搭助手。你必须严格且仅输出JSON格式，严禁包含任何Markdown标记或其他文字。" +
                "如果用户提出修改要求（如换颜色/换单品），你必须从下面提供的衣柜列表中选择合适的单品进行替换搭配。" +
                "【严厉警告】：必须且只能生成 1 套推荐搭配！绝对不允许推荐多套！" +
                "JSON结构必须为：{\"style\":\"...\",\"suggestions\":[\"...\"],\"recommendations\":[{\"id\": 123, \"title\":\"...\",\"desc\":\"...\",\"image\":\"...\"}]}，其中id必须是你从衣柜中挑选的单品ID。";

        if (!history.isEmpty() && "system".equals(history.get(0).getRole())) {
            history.get(0).setContent(systemInstruction);
        } else {
            history.add(0, new DoubaoMessage("system", systemInstruction));
        }

        // 构造衣柜提示字符串
        StringBuilder wardrobeContext = new StringBuilder("\n\n【我的衣柜单品如下，请根据要求从中挑选ID组合】：\n");
        for (WardrobeItem item : wardrobeItems) {
            wardrobeContext.append("ID: ").append(item.getId())
                    .append(" - 类别: ").append(item.getCategoryMain() != null ? item.getCategoryMain() : "未知")
                    .append("/").append(item.getCategorySub() != null ? item.getCategorySub() : "未知")
                    .append(" - 颜色: ").append(item.getColor() != null ? item.getColor() : "未知")
                    .append("\n");
        }

        // 3. 构造当前轮次用户消息
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        String finalMessage = request.getMessage() + wardrobeContext.toString();
        
        if (request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
            userMsg.setContent(finalMessage);
        } else {
            List<DoubaoContentPart> parts = new ArrayList<>();
            parts.add(DoubaoContentPart.text(finalMessage));
            for (String url : request.getImageUrls()) {
                String fmt = doubaoUtil.resolveAndFormatImage(url);
                if (fmt != null) parts.add(DoubaoContentPart.image(fmt));
            }
            userMsg.setContent(parts);
        }
        history.add(userMsg);

        // 4. 调用 AI 网关获取大模型响应
        String aiResponse = doubaoUtil.chatWithVision(history);

        // 5. 精简当前发言后保存历史 (剥离衣柜长文本防止后续轮次Token超发)
        if (request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
            userMsg.setContent(request.getMessage());
        } else {
            @SuppressWarnings("unchecked")
            List<DoubaoContentPart> parts = (List<DoubaoContentPart>) userMsg.getContent();
            if (parts != null && !parts.isEmpty()) {
                parts.set(0, DoubaoContentPart.text(request.getMessage()));
            }
        }
        history.add(new DoubaoMessage("assistant", aiResponse));
        saveHistory(redisKey, history);

        // 6. 强力清洗JSON与重绘流拦截
        try {
            String cleanedJson = cleanJsonString(aiResponse);
            // 自动从历史上下文中挖取首图作为基底
            String mainBase64 = extractMainImageFromHistory(history);
            // 将JSON结果、基底主图、衣柜ID传入拦截器，触发 Seedream 重绘新效果图
            cleanedJson = enhanceChatJsonWithImage(cleanedJson, mainBase64, wardrobeItems);
            
            return objectMapper.readValue(cleanedJson, AiOutfitRecommendVO.class);
        } catch (Exception e) {
            log.error("[AiAssistant] AI 响应解析失败. 原始内容: {}", aiResponse, e);
            throw new BusinessException("AI响应格式解析失败，请重新表达您的需求");
        }
    }

    /**
     * 为对话流专门定制的图像增强：拦截JSON，获取被挑选的衣柜图片Base64，调用Seedream生成新效果图
     */
    private String enhanceChatJsonWithImage(String aiJson, List<String> userImageUrls, List<WardrobeItem> wardrobeItems) {
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

            // 只处理第一套推荐
            if (recommendations.size() > 0) {
                ObjectNode itemNode = (ObjectNode) recommendations.get(0);
                List<String> fusionBase64s = new ArrayList<>();

                // 1. 如果用户本次对话传了图片，作为主参考图加入
                if (userImageUrls != null && !userImageUrls.isEmpty()) {
                    String mainB64 = downloadToBase64(userImageUrls.get(0));
                    if (mainB64 != null) fusionBase64s.add(mainB64);
                }

                // 2. 提取大模型从衣柜中挑选的单品 ID，获取原图 Base64
                if (itemNode.has("id") && !itemNode.get("id").isNull()) {
                    Long itemId = itemNode.path("id").asLong();
                    wardrobeItems.stream()
                            .filter(i -> i.getId().equals(itemId))
                            .findFirst()
                            .ifPresent(i -> {
                                String b64 = downloadToBase64(i.getOriginalImageUrl());
                                if (b64 != null) fusionBase64s.add(b64);
                            });
                }

                // 3. 调用 Seedream 绘图大模型重新生成效果图
                if (!fusionBase64s.isEmpty()) {
                    String drawPrompt = "将这些衣服单品进行搭配，生成一套完整的全身的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                    String effectUrl = seedreamUtil.generateImageFromMultipleBase64(drawPrompt, fusionBase64s);
                    itemNode.put("image", effectUrl);
                }
            }
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("[AiAssistant] 对话流 JSON 图像增强异常: {}", e.getMessage());
            return aiJson; // 如果生成失败，优雅降级，返回原纯文本JSON
        }
    }

    private List<DoubaoMessage> loadHistory(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<DoubaoMessage>>() {});
        } catch (Exception e) {
            log.warn("[AiAssistant] 历史加载失败，重置会话: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveHistory(String key, List<DoubaoMessage> history) {
        try {
            // 对话截断防御 (保留最近 20 轮)
            if (history.size() > 20) {
                history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            }
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(history), EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("[AiAssistant] 历史保存失败: {}", e.getMessage());
        }
    }

    private String cleanJsonString(String raw) {
        if (raw == null) return "{}";
        // 强力正则截取：无视大模型的前后废话，只提取最外层 {} 结构
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start != -1 && end != -1 && start <= end) {
            return raw.substring(start, end + 1);
        }
        return "{}";
    }

    // 从 Redis 历史中提取用户第一轮上传的 Base64 参照主图
    private String extractMainImageFromHistory(List<DoubaoMessage> history) {
        for (DoubaoMessage msg : history) {
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                try {
                    String jsonStr = objectMapper.writeValueAsString(msg.getContent());
                    JsonNode node = objectMapper.readTree(jsonStr);
                    if (node.isArray()) {
                        for (JsonNode part : node) {
                            if ("image_url".equals(part.path("type").asText())) {
                                return part.path("image_url").path("url").asText();
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore Jackson parse issues for safe extraction
                }
            }
        }
        return null;
    }

    // 为对话流专门定制的图像增强拦截器 (支持 Seedream 二次重绘)
    private String enhanceChatJsonWithImage(String aiJson, String mainBase64, List<WardrobeItem> wardrobeItems) {
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

            if (recommendations.size() > 0) {
                ObjectNode itemNode = (ObjectNode) recommendations.get(0);
                List<String> fusionBase64s = new ArrayList<>();

                // 1. 注入上一轮/当前轮的用户主参考图
                if (mainBase64 != null) {
                    fusionBase64s.add(mainBase64);
                }

                // 2. 提取大模型从衣柜中挑选替换的单品 ID，获取原图 Base64
                if (itemNode.has("id")) {
                    Long itemId = itemNode.path("id").asLong();
                    wardrobeItems.stream()
                            .filter(i -> i.getId().equals(itemId))
                            .findFirst()
                            .ifPresent(i -> {
                                String b64 = downloadToBase64(i.getOriginalImageUrl());
                                if (b64 != null) fusionBase64s.add(b64);
                            });
                }

                // 3. 调用 Seedream 重新融合生图
                if (!fusionBase64s.isEmpty()) {
                    String drawPrompt = "将这些衣服单品进行搭配，生成一套完整的全身的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                    String effectUrl = seedreamUtil.generateImageFromMultipleBase64(drawPrompt, fusionBase64s);
                    itemNode.put("image", effectUrl);
                }
            }
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("[AiAssistant] 对话流 JSON 图像增强重绘异常: {}", e.getMessage());
            return aiJson;
        }
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
                            if (b64 != null) fusionBase64s.add(b64);
                        });

                String drawPrompt = "将这些衣服单品进行搭配，生成一套完整的全身的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                String effectUrl = seedreamUtil.generateImageFromMultipleBase64(drawPrompt, fusionBase64s);
                itemNode.put("image", effectUrl);
            }
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("[AiAssistant] JSON 增强异常: {}", e.getMessage());
            return aiJson;
        }
    }

    private String downloadToBase64(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(URI.create(imageUrl), byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(response.getBody());
            }
        } catch (Exception e) {
             log.warn("[AiAssistant] 下载跳过: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String chat(String message, String sessionId, Long userId) {
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;
        List<DoubaoMessage> history = loadHistory(redisKey);

        if (history.isEmpty()) {
            history.add(new DoubaoMessage("system", "你是一位专业的校园穿搭顾问。"));
        }

        history.add(new DoubaoMessage("user", message));
        String reply = doubaoUtil.chatWithVision(history);
        history.add(new DoubaoMessage("assistant", reply));
        saveHistory(redisKey, history);
        return reply;
    }
}
