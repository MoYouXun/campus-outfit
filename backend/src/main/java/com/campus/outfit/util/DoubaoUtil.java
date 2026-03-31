package com.campus.outfit.util;

import com.campus.outfit.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;

/**
 * 豆包大模型网关工具类 - 无状态组件
 */
@Slf4j
@Component
public class DoubaoUtil {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-lite}")
    private String endpointId;

    @Value("${api.doubao.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    public DoubaoUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 使用原生 HTTP 引擎确保长耗时请求稳定性
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(40000); 
        factory.setReadTimeout(120000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 【内部 DTO】豆包/OpenAI 消息格式
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoubaoMessage {
        private String role;
        /**
         * content 字段的多态实现：
         * 1. 简单文本：String
         * 2. 多模态内容：List<DoubaoContentPart>
         */
        private Object content;
    }

    /**
     * 【内部 DTO】多模态内容节点
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DoubaoContentPart {
        private String type; // text 或 image_url
        private String text;
        private Map<String, String> image_url;

        public static DoubaoContentPart text(String text) {
            return new DoubaoContentPart("text", text, null);
        }

        public static DoubaoContentPart image(String url) {
            Map<String, String> urlMap = new HashMap<>();
            urlMap.put("url", url);
            return new DoubaoContentPart("image_url", null, urlMap);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoubaoChatRequest {
        private String model;
        private List<DoubaoMessage> messages;
    }

    /**
     * 通用多模态交互接口 (无状态)
     * @param messages 已由业务层拼装完毕的消息数组
     * @return 响应 Content 文本
     */
    public String chatWithVision(List<DoubaoMessage> messages) {
        log.info("[DoubaoUtil] 发起 AI 会话请求, messages.size: {}", (messages != null ? messages.size() : 0));
        try {
            return callDoubaoApiWithRetry(messages);
        } catch (Exception e) {
            log.error("[DoubaoUtil] 接口调用链路崩溃: {}", e.getMessage());
            throw new BusinessException("AI 服务通讯异常: " + e.getMessage());
        }
    }

    /**
     * 【辅助工具】处理并标准化图片资源（支持 URL 或 Base64）
     */
    public String resolveAndFormatImage(String imageSource) {
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
            log.warn("[DoubaoUtil] 素材预处理跳过: {}", e.getMessage());
        }
        return null;
    }

    private String callDoubaoApiWithRetry(List<DoubaoMessage> messages) throws Exception {
        int maxRetries = 3;
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return callDoubaoApi(messages);
            } catch (ResourceAccessException | BusinessException e) {
                lastException = e;
                log.warn("[DoubaoUtil] API 通讯抖动, 发起第 {} 次重试...", i + 1);
                if (i < maxRetries - 1) Thread.sleep(1500);
            }
        }
        throw (lastException != null ? lastException : new BusinessException("AI 响应重试枯竭"));
    }

    private String callDoubaoApi(List<DoubaoMessage> messages) throws Exception {
        String url = baseUrl + "/chat/completions";
        log.info("[DoubaoUtil] 正在请求 API: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);
        
        DoubaoChatRequest requestBody = new DoubaoChatRequest(endpointId, messages);
        HttpEntity<DoubaoChatRequest> entity = new HttpEntity<>(requestBody, headers);
        
        String response = restTemplate.postForObject(url, entity, String.class);
        JsonNode root = objectMapper.readTree(response);
        if (root.has("error")) throw new BusinessException(root.path("error").path("message").asText());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
