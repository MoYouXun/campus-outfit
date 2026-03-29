package com.campus.outfit.util;

import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 豆包 AI 聊天工具类，基于 Redis 存储对话上下文
 */
@Slf4j
@Component
public class DoubaoUtil {

    private final StringRedisTemplate redisTemplate;
    private final WardrobeItemMapper wardrobeItemMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-lite}")
    private String endpointId;

    private static final String CONTEXT_KEY_PREFIX = "ai:context:";
    private static final long EXPIRE_HOURS = 2;

    /**
     * 手动构造函数：初始化并配置具有 120s 超时保护的 RestTemplate
     */
    public DoubaoUtil(StringRedisTemplate redisTemplate, WardrobeItemMapper wardrobeItemMapper, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.wardrobeItemMapper = wardrobeItemMapper;
        this.objectMapper = objectMapper;
        
        // 创建带有超时配置的请求工厂
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10秒连接超时
        // 这里配置 120s 超时是为了防止大模型长文本生成造成的 Socket EOF 断联
        factory.setReadTimeout(120000);    // 120秒读取超时
        
        this.restTemplate = new RestTemplate(factory);
        log.info("[DoubaoUtil] RestTemplate 初始化完成，读取超时设置为 120s");
    }

    /**
     * 与 AI 进行对话，结合用户衣柜数据
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param userInput 用户输入的文本
     * @return AI 响应内容
     */
    public String chat(Long userId, String sessionId, String userInput) {
        return chat(userId, sessionId, userInput, null);
    }

    /**
     * 分析图片并进行对话
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param userInput 用户输入的文本或提示词
     * @param base64Image 可选的图片 Base64
     * @return AI 响应内容
     */
    public String chat(Long userId, String sessionId, String userInput, String base64Image) {
        log.info("[DoubaoUtil] 开始对话请求, userId: {}, sessionId: {}, 包含图片: {}", userId, sessionId, (base64Image != null));
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;

        try {
            // 1. 获取用户衣柜数据 (用于增强 Prompt)
            List<WardrobeItem> items = wardrobeItemMapper.selectList(
                    new LambdaQueryWrapper<WardrobeItem>().eq(WardrobeItem::getUserId, userId)
            );
            
            // 2. 从 Redis 获取或初始化对话历史
            String historyJson = redisTemplate.opsForValue().get(redisKey);
            List<Map<String, Object>> history;
            if (historyJson == null) {
                history = new ArrayList<>();
                // 初始化系统提示词，包含衣柜上下文
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                StringBuilder systemContent = new StringBuilder("你是一位专业的校园穿搭顾问。请务必以 JSON 格式返回分析结果，包含以下字段：style (整体风格), suggestions (建议列表，字符串数组), recommendations (推荐单品列表，每个单品包含项: title (单品名称), desc (推荐理由), image (预留空字符串，由后端填充))。\n");
                if (items != null && !items.isEmpty()) {
                    systemContent.append("用户的衣柜中有以下单品：\n");
                    for (WardrobeItem item : items) {
                        systemContent.append("- ").append(item.getCategoryMain())
                                .append(" (").append(item.getCategorySub()).append("), 颜色: ")
                                .append(item.getColor()).append("\n");
                    }
                }
                systemMsg.put("content", systemContent.toString());
                history.add(systemMsg);
            } else {
                history = objectMapper.readValue(historyJson, new TypeReference<List<Map<String, Object>>>() {});
            }

            // 3. 构建用户消息 (支持多模态)
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            
            if (base64Image != null) {
                List<Map<String, Object>> contentList = new ArrayList<>();
                Map<String, Object> textPart = new HashMap<>();
                textPart.put("type", "text");
                textPart.put("text", userInput);
                contentList.add(textPart);

                Map<String, Object> imgPart = new HashMap<>();
                imgPart.put("type", "image_url");
                Map<String, String> urlMap = new HashMap<>();
                urlMap.put("url", resolveAndFormatImage(base64Image));
                imgPart.put("image_url", urlMap);
                contentList.add(imgPart);
                
                userMsg.put("content", contentList);
            } else {
                userMsg.put("content", userInput);
            }
            
            history.add(userMsg);

            // 4. 调用豆包 API
            String aiReply = callDoubaoApi(history);

            // 5. 将助手回复加入历史并更新 Redis
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiReply);
            history.add(assistantMsg);

            // 限制对话历史长度以防 Token 溢出，仅保留最近 20 条
            if (history.size() > 20) {
                history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            }

            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(history), EXPIRE_HOURS, TimeUnit.HOURS);

            return aiReply;

        } catch (Exception e) {
            log.error("[DoubaoUtil] 请求失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 对话服务异常", e);
        }
    }

    private String resolveAndFormatImage(String imageSource) {
        if (imageSource == null || imageSource.isEmpty()) {
            return "";
        }
        
        // 1. 如果是 HTTP/HTTPS 链接，先下载图片再转 Base64
        if (imageSource.startsWith("http://") || imageSource.startsWith("https://")) {
            try (java.io.InputStream is = java.net.URI.create(imageSource).toURL().openStream()) {
                byte[] imageBytes = is.readAllBytes();
                if (imageBytes != null) {
                    String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
                    return "data:image/jpeg;base64," + base64;
                }
            } catch (Exception e) {
                log.error("AI 助手下载图片流失败, URL: {}", imageSource, e);
                throw new RuntimeException("读取图片进行 Base64 编码失败: " + e.getMessage());
            }
        }
        
        // 2. 如果本身就是 Base64 字符串，清洗多余前缀
        String cleanBase64 = imageSource.replaceAll("(?i)^data:image/[^;]+;base64,", "");
        while (cleanBase64.toLowerCase().startsWith("data:image/")) {
            cleanBase64 = cleanBase64.replaceAll("(?i)^data:image/[^;]+;base64,", "");
        }
        cleanBase64 = cleanBase64.replaceAll("[\\r\\n\\s]", "");
        
        return "data:image/jpeg;base64," + cleanBase64;
    }

    private String callDoubaoApi(List<Map<String, Object>> messages) throws Exception {
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String responseStr = restTemplate.postForObject(url, entity, String.class);
        
        JsonNode root = objectMapper.readTree(responseStr);
        if (root.has("error")) {
            throw new RuntimeException("豆包 API 返回错误: " + root.path("error").path("message").asText());
        }

        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
