package com.campus.outfit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 火山引擎 Seedream 图像生成工具类 (基于豆包/方舟最新 API)
 */
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
        // 配置具有 60 秒超时时间的 RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10秒连接超时
        factory.setReadTimeout(60000);    // 60秒读取超时
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 从多个 Base64 图片生成图像 (使用豆包 Seedream 4.5 接口)
     * @param base64Images Base64 图片列表
     * @return 生成的网络图片 URL
     */
    public String generateImageFromMultipleBase64(List<String> base64Images) {
        log.info("[SeedreamUtil] 开始多图融合生图请求, endpoint: {}, 包含图片张数: {}", endpointId, base64Images.size());

        try {
            // 1. 准备并格式化参考图片
            List<String> formattedBase64List = new ArrayList<>();
            for (String raw : base64Images) {
                formattedBase64List.add(resolveAndFormatImage(raw));
            }

            // 2. 构造 Ark 平台图像生成请求 Payload
            String url = "https://ark.cn-beijing.volces.com/api/v3/images/generations";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(doubaoKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", endpointId);
            requestBody.put("prompt", "基于以下图片进行穿搭融合和生图，保持风格协调");
            // Seedream 4.5/5.0 常用参考图字段为 reference_images
            requestBody.put("reference_images", formattedBase64List);
            requestBody.put("response_format", "url");

            log.info("[SeedreamUtil] 正在向豆包接口发起 POST 请求...");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            log.info("[SeedreamUtil] 接收到响应内容");

            // 3. 解析响应结果
            JsonNode root = objectMapper.readTree(responseStr);
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText();
                log.error("[SeedreamUtil] 豆包 API 返回错误: {}", errorMsg);
                throw new RuntimeException("豆包 API 异常: " + errorMsg);
            }

            // 提取 data[0].url
            JsonNode dataNode = root.path("data");
            if (dataNode.isArray() && !dataNode.isEmpty()) {
                String resultUrl = dataNode.get(0).path("url").asText();
                log.info("[SeedreamUtil] 图像生成成功, 返回 URL: {}", resultUrl);
                return resultUrl;
            } else {
                log.error("[SeedreamUtil] 响应报文中未包含有效的图片数据: {}", responseStr);
                throw new RuntimeException("豆包 API 未返回预期的图片链接");
            }

        } catch (Exception e) {
            log.error("[SeedreamUtil] 图像生成全流程异常: {}", e.getMessage(), e);
            throw new RuntimeException("Seedream 生成服装融合图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 图片地址解析辅助方法：支持 HTTP 下载或 Base64 清洗
     */
    private String resolveAndFormatImage(String imageSource) {
        if (imageSource == null || imageSource.isEmpty()) {
            return "";
        }
        
        try {
            byte[] imageBytes = null;
            // 1. 如果是 HTTP/HTTPS 链接，先下载图片
            if (imageSource.startsWith("http://") || imageSource.startsWith("https://")) {
                try (java.io.InputStream is = java.net.URI.create(imageSource).toURL().openStream()) {
                    imageBytes = is.readAllBytes();
                } catch (Exception e) {
                    log.error("[SeedreamUtil] 下载图片失败, URL: {}", imageSource, e);
                    throw new RuntimeException("无法读取并下载网络图片: " + e.getMessage());
                }
            } else {
                // 2. 如果本身就是 Base64 字符串，解码为字节数组以便后续统一压缩
                String cleanBase64 = imageSource.replaceAll("(?i)^data:image/[^;]+;base64,", "");
                while (cleanBase64.toLowerCase().startsWith("data:image/")) {
                    cleanBase64 = cleanBase64.replaceAll("(?i)^data:image/[^;]+;base64,", "");
                }
                cleanBase64 = cleanBase64.replaceAll("[\\r\\n\\s]", "");
                imageBytes = java.util.Base64.getDecoder().decode(cleanBase64);
            }

            if (imageBytes != null && imageBytes.length > 0) {
                // 3. 统一进行压缩处理 (使用项目中已有的 Thumbnailator)
                // 限制最大分辨率为 1280x1280，质量 0.8，强制转码为 JPG 以减小体积
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                net.coobird.thumbnailator.Thumbnails.of(new java.io.ByteArrayInputStream(imageBytes))
                        .size(1280, 1280)
                        .outputFormat("jpg")
                        .outputQuality(0.8)
                        .toOutputStream(baos);
                
                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                return "data:image/jpeg;base64," + base64;
            }
            
        } catch (Exception e) {
            log.error("[SeedreamUtil] 处理并压缩图片失败: {}", e.getMessage(), e);
            // 兜底：如果处理失败，尝试以原样返回（可能仍会触发 Socket 异常，但保证了代码执行不中断）
            return imageSource;
        }

        return "";
    }
}
