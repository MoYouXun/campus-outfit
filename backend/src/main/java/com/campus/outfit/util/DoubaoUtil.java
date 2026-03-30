package com.campus.outfit.util;

import com.campus.outfit.exception.BusinessException;
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
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DoubaoUtil {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-lite}")
    private String endpointId;

    private static final String CONTEXT_KEY_PREFIX = "ai:context:";
    private static final long EXPIRE_HOURS = 2;

    public DoubaoUtil(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(40000); // 调大至 40s 以应对极端网络抖动
        factory.setReadTimeout(120000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String chat(Long userId, String sessionId, String userInput) {
        return chat(userId, sessionId, userInput, (List<String>) null);
    }

    public String chat(Long userId, String sessionId, String userInput, String base64Image) {
        List<String> images = base64Image != null ? Collections.singletonList(base64Image) : null;
        return chat(userId, sessionId, userInput, images);
    }

    public String chat(Long userId, String sessionId, String userInput, List<String> contextBase64Images) {
        log.info("[DoubaoUtil] 开始对话请求, sessionId: {}", sessionId);
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;

        try {
            String historyJson = redisTemplate.opsForValue().get(redisKey);
            List<Map<String, Object>> history;
            if (historyJson == null) {
                history = new ArrayList<>();
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一位专业的校园穿搭顾问。请务必以 JSON 格式返回分析结果，包含字段：style, suggestions, recommendations (含 title, desc, image)。");
                history.add(systemMsg);
            } else {
                history = objectMapper.readValue(historyJson, new TypeReference<List<Map<String, Object>>>() {});
            }

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            List<Map<String, Object>> contentList = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", userInput);
            contentList.add(textPart);

            if (contextBase64Images != null) {
                for (String src : contextBase64Images) {
                    String formatted = resolveAndFormatImage(src);
                    if (formatted != null) {
                        Map<String, Object> imgPart = new HashMap<>();
                        imgPart.put("type", "image_url");
                        Map<String, String> urlMap = new HashMap<>();
                        urlMap.put("url", formatted);
                        imgPart.put("image_url", urlMap);
                        contentList.add(imgPart);
                    }
                }
            }
            
            userMsg.put("content", contentList);
            history.add(userMsg);

            String aiReply = callDoubaoApiWithRetry(history);

            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiReply);
            history.add(assistantMsg);

            if (history.size() > 20) history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(history), EXPIRE_HOURS, TimeUnit.HOURS);
            return aiReply;

        } catch (Exception e) {
            log.error("[DoubaoUtil] 对话链路崩溃: {}", e.getMessage());
            throw new BusinessException("AI 服务故障或超时: " + e.getMessage());
        }
    }

    private String resolveAndFormatImage(String imageSource) {
        if (imageSource == null || imageSource.isEmpty()) return null;
        try {
            byte[] imageBytes = null;
            if (imageSource.startsWith("http")) {
                imageBytes = restTemplate.getForObject(java.net.URI.create(imageSource), byte[].class);
            } else {
                String cleanBase64 = imageSource.replaceAll("(?i)^data:image/[^;]+;base64,", "").replaceAll("[\\r\\n\\s]", "");
                imageBytes = java.util.Base64.getDecoder().decode(cleanBase64);
            }

            if (imageBytes == null || imageBytes.length < 4) return null;
            boolean isJpeg = (imageBytes[0] == (byte)0xFF && imageBytes[1] == (byte)0xD8);
            boolean isPng = (imageBytes[0] == (byte)0x89 && imageBytes[1] == (byte)0x50);
            if (!isJpeg && !isPng) return null;

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            net.coobird.thumbnailator.Thumbnails.of(new java.io.ByteArrayInputStream(imageBytes))
                    .size(1024, 1024).outputFormat("jpg").outputQuality(0.7).toOutputStream(baos);
            return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.warn("[DoubaoUtil] 图片处理受阻: {}", e.getMessage());
        }
        return null;
    }

    private String callDoubaoApiWithRetry(List<Map<String, Object>> messages) throws Exception {
        int maxRetries = 3;
        Exception lastException = new BusinessException("AI 服务调用重试失败");
        for (int i = 0; i < maxRetries; i++) {
            try {
                return callDoubaoApi(messages);
            } catch (ResourceAccessException | BusinessException e) {
                lastException = e;
                log.warn("[DoubaoUtil] 第 {} 次调用 AI 失败, 正在重试...", i + 1);
                if (i < maxRetries - 1) Thread.sleep(1500);
            }
        }
        throw lastException;
    }

    private String callDoubaoApi(List<Map<String, Object>> messages) throws Exception {
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);
        Map<String, Object> body = new HashMap<>();
        body.put("model", endpointId);
        body.put("messages", messages);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String response = restTemplate.postForObject(url, entity, String.class);
        JsonNode root = objectMapper.readTree(response);
        if (root.has("error")) throw new BusinessException(root.path("error").path("message").asText());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
