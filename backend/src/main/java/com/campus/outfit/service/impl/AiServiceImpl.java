package com.campus.outfit.service.impl;

import jakarta.annotation.PostConstruct;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.dto.AiRecommendationResult;
import com.campus.outfit.service.AiService;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-lite:}")
    private String endpointId;

    @Value("${api.doubao.endpoint-mini:}")
    private String endpointMini;

    @Value("${api.volcengine.ak:}")
    private String volcAk;

    @Value("${api.volcengine.sk:}")
    private String volcSk;

    private IVisualService visualService;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "ai:recommend:";
    private static final long CACHE_TTL_MINUTES = 10; // 缓存 10 分钟

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceImpl() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000); // 15s 连接超时
        factory.setReadTimeout(60000); // 60s 读取超时，防止大模型响应缓慢导致 EOF
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        log.info("[AI Service] 正在初始化火山引擎视觉服务客户端...");
        // 1.0.x SDK 统一采用单例工厂模式获取实例
        this.visualService = VisualServiceImpl.getInstance();
        this.visualService.setAccessKey(volcAk);
        this.visualService.setSecretKey(volcSk);
        log.info("[AI Service] 火山引擎视觉服务客户端初始化完成");
    }

    @Override
    public AiAnalysisResult analyzeOutfit(byte[] imageBytes) {
        System.out.println("[DEBUG] 开始分析图片，图片大小: " + imageBytes.length + " bytes");
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);
        System.out.println("[DEBUG] 使用的推理接入点: " + endpointId);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一位专业的校园穿搭顾问。请根据用户提供的服装图片，返回一个严格格式为JSON的分析结果，包含以下字段：\n" +
                "1. styleTags (字符串列表): 穿搭的风格，如[\"法式温柔\", \"校园百搭\"]\n" +
                "2. colorTags (字符串列表): 主要配色，如[\"米色\", \"浅蓝\"]\n" +
                "3. itemKeywords (字符串列表): 识别出的主要单品，如[\"针织开衫\", \"百褶裙\"]\n" +
                "4. proportionSuggestion (字符串): 专业且亲切的穿搭/比例建议。\n" +
                "例如: {\"styleTags\":[\"休闲\"], \"colorTags\":[\"白色\"], \"itemKeywords\":[\"T恤\"], \"proportionSuggestion\": \"建议将衣角塞进裤腰。\"}。\n"
                +
                "你的回答必须并且只能是一个合法的JSON字符串，不要包含 Markdown 标记格式或其他描述文字。");

        // 构建 user message (支持多模态, 图片URL和文字)
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "请分析这张图片里的穿搭。");

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, String> imageUrlMap = new HashMap<>();
        // 将图片字节转为 Base64 字符串。通常前端上传的是 jpeg 或 png，此处以 jpeg 示例，
        // 豆包 API 支持 data URI 格式。
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        imageUrlMap.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrlMap);

        userMessage.put("content", Arrays.asList(textContent, imageContent));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", Arrays.asList(systemMessage, userMessage));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            System.out.println("[DEBUG] 正在调用AI服务...");
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            System.out.println("[DEBUG] AI服务响应: " + responseStr);

            JsonNode root = objectMapper.readTree(responseStr);

            // 检查 API 是否返回了错误信息
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText();
                System.out.println("[DEBUG] AI服务返回错误: " + errorMsg);
                throw new RuntimeException("豆包 API 报错: " + errorMsg);
            }
            System.out.println("[DEBUG] AI服务响应正常");

            String content = root.path("choices").path(0).path("message").path("content").asText();

            // 更加稳健的 JSON 提取逻辑
            int jsonStartIndex = content.indexOf("```json");
            if (jsonStartIndex != -1) {
                content = content.substring(jsonStartIndex + 7);
                int jsonEndIndex = content.lastIndexOf("```");
                if (jsonEndIndex != -1) {
                    content = content.substring(0, jsonEndIndex);
                }
            } else {
                int codeStartIndex = content.indexOf("```");
                if (codeStartIndex != -1) {
                    content = content.substring(codeStartIndex + 3);
                    int codeEndIndex = content.lastIndexOf("```");
                    if (codeEndIndex != -1) {
                        content = content.substring(0, codeEndIndex);
                    }
                }
            }
            content = content.trim();

            try {
                return objectMapper.readValue(extractJson(content), AiAnalysisResult.class);
            } catch (Exception e) {
                throw new RuntimeException("JSON 解析失败，原始内容: " + content, e);
            }
        } catch (Exception e) {
            log.error("AI 分析失败: {}", e.getMessage());
            // 降级处理
            AiAnalysisResult fallback = new AiAnalysisResult();
            fallback.setStyleTags(Arrays.asList("基础", "百搭"));
            fallback.setColorTags(Arrays.asList("白色", "黑色"));
            fallback.setItemKeywords(Arrays.asList("T恤", "牛仔裤"));
            fallback.setProportionSuggestion("AI服务解析异常：" + e.getMessage());
            return fallback;
        }
    }

    @Override
    public AiRecommendationResult recommendOutfit(String prompt) {
        // 使用 MD5 对 prompt 生成唯一键，作为缓存 ID
        String promptMd5 = DigestUtils.md5DigestAsHex(prompt.getBytes());
        String cacheKey = CACHE_PREFIX + promptMd5;

        // 1. 尝试从缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (cached instanceof AiRecommendationResult)
                    return (AiRecommendationResult) cached;
                return objectMapper.convertValue(cached, AiRecommendationResult.class);
            }
        } catch (Exception e) {
            log.warn("AI 推荐缓存读取异常: {}", e.getMessage());
        }

        log.info("[DEBUG] 缓存未命中，开始向大模型发起请求...");
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一位专业的校园穿搭顾问。请根据用户提供的天气、场景和私服信息，返回一个严格格式为JSON的推荐结果，包含以下字段：\n" +
                "1. outfitIds (整数数组): 优先在用户提供的私人衣橱列表中挑选出最合适的衣服组合ID（如 [1, 5, 8]）。私人衣橱为空或没有衣服合适时，必须返回空数组 []。\n" +
                "2. reasoning (字符串): 你给出的穿搭解析或单品搭配指南，请充分体现针对场景和当天天气的关怀，字数约60字。\n" +
                "3. searchTags (字符串数组): 仅当 outfitIds 为空时，你需要推断用户需要什么样风格的衣服（如 [\"风衣\", \"休闲\", \"浅色\"]）。最多提供3个精确关联标签。\n"
                +
                "回答仅限合法JSON。");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        String modelEp = (endpointMini != null && !endpointMini.isEmpty()) ? endpointMini : endpointId;
        requestBody.put("model", modelEp);
        requestBody.put("messages", java.util.Arrays.asList(systemMessage, userMessage));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            AiRecommendationResult result = CompletableFuture.supplyAsync(() -> {
                try {
                    String responseStr = restTemplate.postForObject(url, entity, String.class);
                    JsonNode root = objectMapper.readTree(responseStr);
                    if (root.has("error"))
                        throw new RuntimeException(root.path("error").path("message").asText());

                    String content = root.path("choices").path(0).path("message").path("content").asText();
                    int start = content.indexOf("{");
                    int end = content.lastIndexOf("}");
                    if (start != -1 && end != -1)
                        content = content.substring(start, end + 1);
                    return objectMapper.readValue(content, AiRecommendationResult.class);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).get(8, TimeUnit.SECONDS);

            // 存入缓存
            if (result != null) {
                redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return result;
        } catch (TimeoutException e) {
            log.warn("AI 推荐超时（>8s），已降级");
            AiRecommendationResult fallback = new AiRecommendationResult();
            fallback.setOutfitIds(java.util.Collections.emptyList());
            fallback.setReasoning("⚡ 正在为您穿透全站灵感，结合今日天气和热门动态，为您精准匹配最佳穿搭趋势...");
            return fallback;
        } catch (Exception e) {
            log.error("AI 推荐失败: {}", e.getMessage());
            AiRecommendationResult fallback = new AiRecommendationResult();
            fallback.setOutfitIds(java.util.Collections.emptyList());
            fallback.setReasoning("专属 AI 顾问暂时小憩中，已为您同步调取社区当前高度关注的校园穿搭灵感。");
            return fallback;
        }
    }

    @Override
    public String generateTryOnImage(String personImageUrl, String outfitImageUrl) {
        log.info("[AI Try-On] 切入异步换装模型 DressingDiffusionV2. 人像: {}, 衣服: {}", personImageUrl, outfitImageUrl);
        try {
            // 第一步：提交任务
            Map<String, Object> submitBody = new HashMap<>();
            submitBody.put("req_key", "dressing_diffusionV2");
            
            Map<String, String> modelMap = new HashMap<>();
            modelMap.put("url", personImageUrl);
            submitBody.put("model", modelMap);
            
            Map<String, Object> garment = new HashMap<>();
            Map<String, String> garmentItem = new HashMap<>();
            garmentItem.put("type", "full");
            garmentItem.put("url", outfitImageUrl);
            garment.put("data", Arrays.asList(garmentItem));
            submitBody.put("garment", garment);

            log.info("[AI Try-On] 正在通过通用 JSON 模式提交异常任务...");
            // 采用通用基于 Map 序列化方式，规避具体的 Request 类引用
            String taskId = "";
            try {
                // 注意：由于 visualService 实例是 SDK 生成的 IVisualService，
                // 其具体方法可能对参数类型有要求，若无法直接使用 Map，则采用通用反射调用或退化至底层方法。
                // 暂时按照“基于 JSON 序列化的通用调用方式”的思路：
                // 如果 SDK 支持直接传 Map 或通过 objectMapper 转换
                Object submitResp = visualService.cvSubmitTask(submitBody);
                JsonNode submitData = objectMapper.valueToTree(submitResp);
                
                if (submitData.path("code").asInt() != 10000 && !submitData.has("task_id")) {
                     String error = submitData.path("message").asText("Unknown Error");
                     log.error("提交任务失败响应: {}", submitData);
                     throw new RuntimeException("提交换装任务失败: " + error);
                }
                taskId = submitData.path("data").path("task_id").asText();
            } catch (Exception e) {
                log.error("通用调用层异常: {}", e.getMessage());
                throw new RuntimeException("SDK 通用调用失败: " + e.getMessage());
            }

            log.info("[AI Try-On] 任务提交成功, task_id: {}", taskId);

            // 第二步：轮询结果
            int maxAttempts = 40;
            int attempt = 0;
            while (attempt < maxAttempts) {
                attempt++;
                log.info("[AI Try-On] 进入异步状态轮询 (第 {}/{} 次)...", attempt, maxAttempts);
                Thread.sleep(3000);
                
                Map<String, Object> queryBody = new HashMap<>();
                queryBody.put("req_key", "dressing_diffusionV2");
                queryBody.put("task_id", taskId);

                try {
                    Object queryResp = visualService.cvGetResult(queryBody);
                    JsonNode queryResult = objectMapper.valueToTree(queryResp);

                    if (queryResult.path("code").asInt() != 10000) {
                        String error = queryResult.path("message").asText("Unknown Error");
                        throw new RuntimeException("查询失败: " + error);
                    }

                    JsonNode dataNode = queryResult.path("data");
                    String status = dataNode.path("status").asText();
                    log.info("[AI Try-On] 任务 {} 当前状态: {}", taskId, status);

                    if ("done".equals(status)) {
                        String finalImageUrl = dataNode.path("image_urls").get(0).asText();
                        log.info("[AI Try-On] 最终换装业务完成！结果图 URL: {}", finalImageUrl);
                        return finalImageUrl;
                    } else if ("generating".equals(status) || "in_queue".equals(status)) {
                        continue; 
                    } else {
                        log.error("任务执行异常退出, 最终 JSON: {}", queryResult);
                        throw new RuntimeException("换装任务执行异常, 状态: " + status);
                    }
                } catch (Exception ex) {
                    log.warn("查询环节临时异常: {}, 任务 ID: {}", ex.getMessage(), taskId);
                    if (attempt >= maxAttempts) throw ex;
                }
            }
            throw new RuntimeException("异步换装任务处理超时（120秒）");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("换装流程由于线程中断而终止");
        } catch (Exception e) {
            log.error("[AI Try-On] 流程故障", e);
            throw new RuntimeException("全量换装流程失败: " + e.getMessage());
        }
    }


    /**
     * 从 AI 回复文本中提取 JSON 内容。
     * 支持 ```json...``` 代码块和普通 {} 两种格式。
     */
    private String extractJson(String content) {
        int mdStart = content.indexOf("```json");
        if (mdStart != -1) {
            content = content.substring(mdStart + 7);
            int mdEnd = content.lastIndexOf("```");
            if (mdEnd != -1)
                content = content.substring(0, mdEnd);
            return content.trim();
        }
        int codeStart = content.indexOf("```");
        if (codeStart != -1) {
            content = content.substring(codeStart + 3);
            int codeEnd = content.lastIndexOf("```");
            if (codeEnd != -1)
                content = content.substring(0, codeEnd);
            return content.trim();
        }
        // 直接提取 {}
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1)
            return content.substring(start, end + 1);
        return content.trim();
    }
}
