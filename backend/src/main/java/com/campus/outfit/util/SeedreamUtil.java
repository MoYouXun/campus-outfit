package com.campus.outfit.util;

import com.campus.outfit.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class SeedreamUtil {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-seedream}")
    private String endpointId;

    public SeedreamUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 【稳定性重构】切换为原生 SimpleClientHttpRequestFactory
        // 原因：OkHttp 在处理 HTTP/2 (火山引擎默认协议) 的长耗时请求时，流管理逻辑偶尔与服务端心跳不一致，导致 CANCEL
        // 原生引擎 (基于 HttpURLConnection) 在处理此类阻塞长连接时表现更为稳健
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(40000); 
        factory.setReadTimeout(180000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String generateImageFromMultipleBase64(String prompt, List<String> base64Images) {
        log.info("[SeedreamUtil] 开始多图融合生图请求 (图片数: {})", (base64Images != null ? base64Images.size() : 0));
        try {
            List<String> formattedBase64List = new ArrayList<>();
            if (base64Images != null) {
                for (String raw : base64Images) {
                    String fmt = resolveAndFormatImage(raw);
                    if (fmt != null) formattedBase64List.add(fmt);
                }
            }

            String url = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(doubaoKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", endpointId);
            requestBody.put("prompt", prompt != null ? prompt : "基于这些图片进行穿搭设计");
            requestBody.put("image", formattedBase64List);
            requestBody.put("response_format", "url");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 使用重试机制
            String responseStr = callSeedreamApiWithRetry(url, entity);
            
            JsonNode root = objectMapper.readTree(responseStr);
            if (root.has("error")) throw new BusinessException(root.path("error").path("message").asText());

            JsonNode dataNode = root.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                return dataNode.get(0).path("url").asText();
            }
            throw new BusinessException("API 响应中缺失图像链接");
        } catch (Exception e) {
            log.error("[SeedreamUtil] 生图链路崩溃: {}", e.getMessage());
            throw new BusinessException("AI 生图异常: " + e.getMessage());
        }
    }

    private String callSeedreamApiWithRetry(String url, HttpEntity<Map<String, Object>> entity) throws Exception {
        int maxRetries = 2;
        Exception lastEx = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return restTemplate.postForObject(url, entity, String.class);
            } catch (ResourceAccessException e) {
                lastEx = e;
                log.warn("[SeedreamUtil] 捕获网络异常 (如 stream reset), 发起第 {} 次重试...", i + 1);
                if (i < maxRetries - 1) Thread.sleep(2000);
            }
        }
        throw lastEx;
    }

    public String generateImage(String prompt, List<String> base64Images) {
        return generateImageFromMultipleBase64(prompt, base64Images);
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
                    .size(1280, 1280).outputFormat("jpg").outputQuality(0.8).toOutputStream(baos);
            return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.warn("[SeedreamUtil] 子图解析跳过: {}", e.getMessage());
        }
        return null;
    }
}
