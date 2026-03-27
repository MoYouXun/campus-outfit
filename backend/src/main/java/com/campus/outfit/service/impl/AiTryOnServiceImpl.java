package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiTryOnRequest;
import com.campus.outfit.dto.AiTryOnResponse;
import com.campus.outfit.service.AiTryOnService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 试衣间服务实现类 - 正式接入火山引擎 (豆包) Doubao-Seedream-5.0-lite 模型
 */
@Slf4j
@Service
public class AiTryOnServiceImpl implements AiTryOnService {

    private static final String API_KEY = "2dd2e18c-9d00-4e1d-829b-e28ea619d74a";
    private static final String MODEL_ENDPOINT = "ep-20260327184050-7dvcv";
    private static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";

    @Override
    public AiTryOnResponse generate(AiTryOnRequest request, Long userId) {
        log.info("[AI Try-On] 用户发起换装请求，用户ID: {}, 类别: {}", userId, request.getCategory());
        log.info("[AI Try-On] 正在组装豆包 Seedream 5.0 请求载荷...");

        try {
            // 1. 获取图片原生 Base64 字符串
            String humanBase64 = encodeImageToBase64(request.getHumanImageUrl());
            String garmentBase64 = encodeImageToBase64(request.getGarmentImageUrl());

            // 2. 构建豆包 API 请求体
            Map<String, Object> body = new HashMap<>();
            body.put("model", MODEL_ENDPOINT);
            body.put("prompt", "Virtual Try-On: 请将参考图中的服装，自然、完美地穿在人物身上。要求：必须保持原图人物的面部特征、姿态特征、肤色以及所有背景元素完全不变，光影融合自然，细节逼真，达到真实试穿效果。");
            body.put("n", 1);
            
            // 火山引擎特有多图输入载荷字段 (拼接 Data URI 前缀以满足 URL 格式校验)
            body.put("image", "data:image/jpeg;base64," + humanBase64);
            body.put("reference_image", "data:image/jpeg;base64," + garmentBase64);

            // 3. 构建无代理 RestTemplate (60s 超时)
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(60000);
            factory.setReadTimeout(60000);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + API_KEY);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 4. 执行请求
            log.info("[AI Try-On] 正在调用火山引擎模型 API...");
            JsonNode root = restTemplate.postForObject(API_URL, entity, JsonNode.class);

            if (root == null || !root.has("data")) {
                throw new RuntimeException("火山引擎响应异常或无数据");
            }

            // 5. 解析结果
            String resultImageUrl = null;
            JsonNode dataNode = root.path("data").get(0);
            if (dataNode.has("url")) {
                resultImageUrl = dataNode.path("url").asText();
            } else if (dataNode.has("b64_json")) {
                String b64 = dataNode.path("b64_json").asText();
                resultImageUrl = "data:image/png;base64," + b64;
            }

            if (resultImageUrl == null || resultImageUrl.isEmpty()) {
                log.error("[AI Try-On] 无法解析图片结果，原始响应: {}", root.toString());
                throw new RuntimeException("AI 生成图片地址解析失败");
            }

            log.info("[AI Try-On] 换装成功，生成图片地址: {}", resultImageUrl);

            AiTryOnResponse response = new AiTryOnResponse();
            response.setResultImageUrl(resultImageUrl);
            return response;

        } catch (Exception e) {
            log.error("[AI Try-On] 豆包模型换装流程异常: {}", e.getMessage());
            throw new RuntimeException("AI 换装服务执行异常：" + e.getMessage());
        }
    }

    /**
     * 将图片 URL 转换为原生 Base64 字符串 (不带 Data URI 前缀)
     */
    private String encodeImageToBase64(String imageUrl) {
        log.info("[AI Try-On] 正在读取图片并转换为原生 Base64: {}", imageUrl);
        try (java.io.InputStream is = new java.net.URL(imageUrl).openStream()) {
            byte[] imageBytes = is.readAllBytes();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.error("[AI Try-On] 图片转换 Base64 失败: {}", imageUrl, e);
            throw new RuntimeException("读取图片进行 Base64 编码失败: " + e.getMessage());
        }
    }
}
