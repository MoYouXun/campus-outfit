package com.campus.outfit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 火山引擎 Seedream 图像生成工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedreamUtil {

    private final ObjectMapper objectMapper;

    @Value("${ai.ark.ak}")
    private String accessKey;

    @Value("${ai.ark.sk}")
    private String secretKey;

    @Value("${ai.ark.model-id}")
    private String modelId;

    private IVisualService visualService;

    @PostConstruct
    public void init() {
        log.info("[SeedreamUtil] 正在初始化火山引擎视觉服务客户端...");
        this.visualService = VisualServiceImpl.getInstance();
        this.visualService.setAccessKey(accessKey);
        this.visualService.setSecretKey(secretKey);
        log.info("[SeedreamUtil] 火山引擎视觉服务客户端初始化完成");
    }

    /**
     * 从多个 Base64 图片生成图像 (常用于图像融合/虚拟试衣)
     * @param base64Images Base64 图片列表
     * @return 生成的网络图片 URL
     */
    public String generateImageFromMultipleBase64(List<String> base64Images) {
        log.info("[SeedreamUtil] 开始基于多图生成图像，输入图片数量: {}", base64Images.size());

        try {
            // 处理 Base64 前缀
            List<String> pureBase64List = new ArrayList<>();
            for (String rawBase64 : base64Images) {
                String base64 = formatBase64Url(rawBase64);
                if (base64.startsWith("data:")) {
                    int commaIndex = base64.indexOf(",");
                    if (commaIndex != -1) {
                        pureBase64List.add(base64.substring(commaIndex + 1));
                        continue;
                    }
                }
                pureBase64List.add(base64);
            }

            // 构造请求体 (参考 dressing_diffusionV2 逻辑)
            Map<String, Object> submitBody = new HashMap<>();
            submitBody.put("req_key", "dressing_diffusionV2"); // 默认为换装模型，可根据 modelId 或业务调整
            submitBody.put("binary_data_base64", pureBase64List);
            
            // 提交任务
            Object submitResp = visualService.cvSubmitTask(submitBody);
            JsonNode submitData = objectMapper.valueToTree(submitResp);
            
            if (submitData.path("code").asInt() != 10000 && !submitData.has("task_id")) {
                throw new RuntimeException("火山引擎任务提交失败: " + submitData.path("message").asText());
            }

            String taskId = submitData.path("data").path("task_id").asText();
            log.info("[SeedreamUtil] 任务提交成功，taskId: {}", taskId);

            // 轮询结果
            int maxAttempts = 60;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                Thread.sleep(3000);
                Map<String, Object> queryBody = new HashMap<>();
                queryBody.put("req_key", "dressing_diffusionV2");
                queryBody.put("task_id", taskId);
                queryBody.put("req_json", "{\"return_url\":true}");

                Object queryResp = visualService.cvGetResult(queryBody);
                JsonNode queryResult = objectMapper.valueToTree(queryResp);

                if (queryResult.path("code").asInt() != 10000) {
                    throw new RuntimeException("火山引擎任务查询异常: " + queryResult.path("message").asText());
                }

                JsonNode dataNode = queryResult.path("data");
                String status = dataNode.path("status").asText();
                if ("done".equals(status)) {
                    JsonNode imageUrls = dataNode.path("image_urls");
                    if (imageUrls.isArray() && !imageUrls.isEmpty()) {
                        String resultUrl = imageUrls.get(0).asText();
                        log.info("[SeedreamUtil] 图像生成成功: {}", resultUrl);
                        return resultUrl;
                    }
                } else if (!"generating".equals(status) && !"in_queue".equals(status)) {
                    throw new RuntimeException("任务执行失败, 状态: " + status);
                }
            }
            throw new RuntimeException("图像生成超时");

        } catch (Exception e) {
            log.error("[SeedreamUtil] 图像产生失败: {}", e.getMessage(), e);
            throw new RuntimeException("图像生成异常", e);
        }
    }

    private String formatBase64Url(String base64) {
        if (base64 == null || base64.isEmpty()) return "";
        // 如果已经自带了 data:image/ 前缀，直接返回原串；否则拼接默认的 jpeg 前缀
        if (base64.startsWith("data:image/")) {
            return base64;
        }
        return "data:image/jpeg;base64," + base64;
    }
}
