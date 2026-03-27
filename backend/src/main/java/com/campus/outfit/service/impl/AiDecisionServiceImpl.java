package com.campus.outfit.service.impl;

import com.campus.outfit.dto.OutfitPkRequest;
import com.campus.outfit.dto.OutfitPkResponse;
import com.campus.outfit.service.AiDecisionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI 决策推断服务实现类
 */
@Slf4j
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    private static final String API_KEY = "2dd2e18c-9d00-4e1d-829b-e28ea619d74a";
    private static final String MODEL_ENDPOINT = "ep-20260325141225-h7lw6";
    private static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    private static final String PK_SYSTEM_PROMPT = 
        "你是一位顶级的私人穿搭顾问。请根据用户提供的搭配A、搭配B图片及目标场景，进行极具专业性的对比分析。\n" +
        "【绝对强制指令】：\n" +
        "1. 你必须严格按照下方的 JSON 结构输出结果，绝对不允许修改 JSON 的 Key 名称（必须使用英文 Key）。\n" +
        "2. 绝对不允许在输出中包含任何 Markdown 标记（如 ```json ）。直接输出首尾带大括号的 JSON 字符串。\n" +
        "3. radarData 中的 scoresA 和 scoresB 必须是包含 5 个 0-100 整数的数组，顺序对应 dimensions。\n" +
        "【输出模板】：\n" +
        "{\n" +
        "  \"winner\": \"A或B\",\n" +
        "  \"reason\": \"50字以内的犀利点评，直接指出胜出原因\",\n" +
        "  \"radarData\": {\n" +
        "    \"dimensions\": [\"正式度\", \"色彩和谐\", \"场景契合\", \"时尚感\", \"保暖度\"],\n" +
        "    \"scoresA\": [80, 70, 60, 90, 85],\n" +
        "    \"scoresB\": [60, 85, 90, 70, 75]\n" +
        "  }\n" +
        "}";

    @Override
    public OutfitPkResponse pkOutfits(OutfitPkRequest request, Long userId) {
        log.info("[AI PK] 发起 A/B 对决请求，用户ID: {}, 场景: {}", userId, request.getScene());
        
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        try {
            // 1. 组装 OpenAI Vision API 格式的请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_ENDPOINT);

            List<Map<String, Object>> messages = new ArrayList<>();
            // 系统提示词
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", PK_SYSTEM_PROMPT);
            messages.add(systemMsg);

            // 用户输入 (文本 + 两张图)
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            List<Map<String, Object>> contentList = new ArrayList<>();

            // 文本部分
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "当前对比场景：" + request.getScene() + "。请参考所附的两张图（搭配 A 和 搭配 B）进行对比分析。");
            contentList.add(textContent);

            // 图片 A
            Map<String, Object> imageAContent = new HashMap<>();
            imageAContent.put("type", "image_url");
            Map<String, String> urlA = new HashMap<>();
            urlA.put("url", encodeImageToBase64DataUri(request.getImageAUrl()));
            imageAContent.put("image_url", urlA);
            contentList.add(imageAContent);

            // 图片 B
            Map<String, Object> imageBContent = new HashMap<>();
            imageBContent.put("type", "image_url");
            Map<String, String> urlB = new HashMap<>();
            urlB.put("url", encodeImageToBase64DataUri(request.getImageBUrl()));
            imageBContent.put("image_url", urlB);
            contentList.add(imageBContent);

            userMsg.put("content", contentList);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            // 2. 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + API_KEY);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 3. 调用 API
            log.info("[AI PK] 正在调用火山引擎模型 API...");
            String responseStr = restTemplate.postForObject(API_URL, entity, String.class);
            
            // 4. 解析结果层级 choices[0].message.content
            JsonNode root = mapper.readTree(responseStr);
            String aiContent = root.path("choices").get(0).path("message").path("content").asText();
            
            log.info("[AI PK] 收到原始响应内容: {}", aiContent);

            // 5. 核心防御逻辑：清洗可能的 Markdown 干扰
            String cleanJsonStr = aiContent.trim();
            // 移除可能存在的 ```json 代码块包裹
            if (cleanJsonStr.contains("```")) {
                cleanJsonStr = cleanJsonStr.replace("```json", "").replace("```", "").trim();
            }

            // 6. 反序列化并返回
            return mapper.readValue(cleanJsonStr, OutfitPkResponse.class);

        } catch (Exception e) {
            log.error("[AI PK] 核心流程异常", e);
            throw new RuntimeException("AI 决策执行异常：" + e.getMessage());
        }
    }

    /**
     * 将图片 URL 转换为 Base64 Data URI
     */
    private String encodeImageToBase64DataUri(String imageUrl) {
        log.info("[AI PK] 开始处理图片 Base64 转换, URL: {}", imageUrl);
        // 使用更稳健的方式下载图片流，防止 RestTemplate 对预签名 URL 的处理导致签名失效
        try (java.io.InputStream is = new java.net.URL(imageUrl).openStream()) {
            byte[] imageBytes = is.readAllBytes();
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            log.info("[AI PK] 转换成功, 字节长度: {}", imageBytes.length);
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            log.error("[AI PK] 转换 Base64 发生致命错误: {}", imageUrl, e);
            throw new RuntimeException("读取图片进行Base64编码失败: " + e.getMessage());
        }
    }
}
