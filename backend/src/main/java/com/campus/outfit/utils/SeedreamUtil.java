package com.campus.outfit.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎 Seedream 生图工具类
 */
@Component
public class SeedreamUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${doubao.apiKey}") // 共用火山方舟 API Key
    private String apiKey;

    // Seedream 2.0 模型节点（示例 endpoint，建议从配置文件读取）
    private final String apiUrl = "https://ark.cn-beijing.volces.com/api/v3/images/generations";

    public SeedreamUtil(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 多图融合生图（将原有穿搭与推荐单品融合生成效果图）
     * @param prompt 融合提示词
     * @param base64Images 所有的图片 Base64 列表（第一张通常是原图）
     * @return 生成结果中获得的临时 URL
     */
    public String generateFusionImage(String prompt, List<String> base64Images) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "doubao-seedream-240928"); // 使用支持多图参考的模型节点
            requestBody.put("prompt", prompt);
            
            // 构建参考图
            List<Map<String, Object>> refImages = new ArrayList<>();
            for (String b64 : base64Images) {
                refImages.add(Map.of("image", "data:image/jpeg;base64," + b64));
            }
            requestBody.put("ref_images", refImages);
            requestBody.put("n", 1);
            requestBody.put("size", "1024x1024");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("data").get(0).path("url").asText();
            } else {
                throw new RuntimeException("Seedream API 调用失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("生图过程出错: " + e.getMessage());
        }
    }
}
