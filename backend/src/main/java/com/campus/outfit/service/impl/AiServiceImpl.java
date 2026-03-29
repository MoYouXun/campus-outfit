package com.campus.outfit.service.impl;

import jakarta.annotation.PostConstruct;
import com.campus.outfit.exception.BusinessException;

import com.campus.outfit.dto.AiAnalysisResult;

import com.campus.outfit.service.AiService;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.campus.outfit.entity.WardrobeItem;


@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${api.doubao.key}")
    private String doubaoKey;

    @Value("${api.doubao.endpoint-lite:}")
    private String endpointId;



    @Value("${api.volcengine.ak:}")
    private String volcAk;

    @Value("${api.volcengine.sk:}")
    private String volcSk;

    private IVisualService visualService;

    @Value("${ai.ark.model-id:doubao-seedream-4-5-251128}")
    private String imageModelId;

    /**
     * 对话上下文缓存，存储各会话的历史消息
     */
    private final Map<String, List<Map<String, Object>>> conversationContexts = new ConcurrentHashMap<>();





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


    /**
     * 内部辅助方法：将本地或远程的网络图片URL下载并转为纯 Base64
     */
    private byte[] downloadImageBytes(String imageUrl) {
        java.net.URI uri = java.net.URI.create(imageUrl);
        ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("无法下载图片，HTTP状态码: " + response.getStatusCode());
        }
        
        byte[] bytes = response.getBody();
        if (bytes.length < 4) {
            throw new BusinessException("图片文件内容过小，只有 " + bytes.length + " 字节！");
        }

        // 校验 Magic Number (魔数)
        boolean isJpeg = (bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8);
        boolean isPng = (bytes[0] == (byte)0x89 && bytes[1] == (byte)0x50 && bytes[2] == (byte)0x4E && bytes[3] == (byte)0x47);

        if (!isJpeg && !isPng) {
            String hexPrefix = String.format("%02X %02X %02X %02X", bytes[0], bytes[1], bytes[2], bytes[3]);
            String textTry = new String(bytes, 0, Math.min(bytes.length, 100), java.nio.charset.StandardCharsets.UTF_8);
            log.error("[AI Try-On] 图片格式非法或被拦截！URL: {}, 文件头(Hex): {}, 文本预览: {}", imageUrl, hexPrefix, textTry);
            throw new BusinessException("图片下载异常，可能由于权限被拦截。内容预览: " + textTry);
        }
        return bytes;
    }

    @Override
    public String generateTryOnImage(String personImageUrl, String outfitImageUrl) {
        log.info("[AI Try-On] 切入异步换装模型 DressingDiffusionV2.");
        try {
            // 1. 下载图片并转 Base64
            byte[] personBytes = downloadImageBytes(personImageUrl);
            byte[] outfitBytes = downloadImageBytes(outfitImageUrl);
            String personBase64 = java.util.Base64.getEncoder().encodeToString(personBytes);
            String outfitBase64 = java.util.Base64.getEncoder().encodeToString(outfitBytes);

            // 2. 构造 Base64 提交请求
            Map<String, Object> submitBody = new HashMap<>();
            submitBody.put("req_key", "dressing_diffusionV2");
            submitBody.put("req_image_store_type", 0);
            submitBody.put("binary_data_base64", Arrays.asList(personBase64, outfitBase64));
            submitBody.put("model", new HashMap<String, String>());
            Map<String, Object> garment = new HashMap<>();
            Map<String, String> garmentItem = new HashMap<>();
            garmentItem.put("type", "full");
            garment.put("data", Arrays.asList(garmentItem));
            submitBody.put("garment", garment);

            // 3. 提交任务
            Object submitResp = visualService.cvSubmitTask(submitBody);
            JsonNode submitData = objectMapper.valueToTree(submitResp);
            int code = submitData.path("code").asInt();
            if (code != 10000 && !submitData.has("task_id")) {
                throw new BusinessException("AI换装提交异常: " + submitData.path("message").asText());
            }
            String taskId = submitData.path("data").path("task_id").asText();

            // 4. 轮询结果
            int maxAttempts = 40;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                Thread.sleep(3000);
                Map<String, Object> queryBody = new HashMap<>();
                queryBody.put("req_key", "dressing_diffusionV2");
                queryBody.put("task_id", taskId);
                queryBody.put("req_json", "{\"return_url\":true}");
                
                Object queryResp = visualService.cvGetResult(queryBody);
                JsonNode queryResult = objectMapper.valueToTree(queryResp);
                
                if (queryResult.path("code").asInt() != 10000) {
                    throw new BusinessException("AI换装轮询异常: " + queryResult.path("message").asText());
                }
                
                JsonNode dataNode = queryResult.path("data");
                String status = dataNode.path("status").asText();
                if ("done".equals(status)) {
                    JsonNode imageUrls = dataNode.path("image_urls");
                    if (imageUrls.isArray() && !imageUrls.isEmpty()) {
                        return imageUrls.get(0).asText();
                    }
                    JsonNode base64s = dataNode.path("binary_data_base64");
                    if (base64s.isArray() && !base64s.isEmpty()) {
                        return "data:image/png;base64," + base64s.get(0).asText();
                    }
                } else if (!"generating".equals(status) && !"in_queue".equals(status)) {
                    throw new BusinessException("换装任务执行失败, 状态: " + status);
                }
            }
            throw new BusinessException("换装任务超时");
        } catch (Exception e) {
            log.error("[AI Try-On] 换装流程失败", e);
            throw new BusinessException("AI换装失败: " + e.getMessage());
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


    @Override
    public String analyzeOutfitWithWardrobe(String base64Image, Long userId, String sessionId, List<WardrobeItem> wardrobeItems) {
        log.info("[AI Service] 开始基于衣柜上下文分析穿搭, sessionId: {}", sessionId);
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        // 1. 系统提示词
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一位专业的校园穿搭顾问。请根据用户提供的主图和衣柜单品图片库，返回一个严格格式为JSON的结果，包含以下字段：\n" +
                "1. style (字符串): 整体穿搭风格关键词\n" +
                "2. items (字符串列表): 识别出的主图中单品\n" +
                "3. occasion (字符串): 建议穿着场合\n" +
                "4. suggestions (字符串): 专业穿搭改进建议\n" +
                "5. recommendations (对象列表): 从用户衣柜图片库中挑选的推荐搭配单品，每个对象包含 id 和 reason。\n" +
                "你的回答必须是一个合法的JSON字符串，不要包含 Markdown 标记格式。数据中禁止包含任何违法违规描述。");

        // 2. 构建多模态内容列表
        List<Map<String, Object>> contentList = new ArrayList<>();
        
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "这是我的穿搭主图。请结合我的衣柜单品（下方补充图片）给出分析及搭配建议。");
        contentList.add(textPart);

        // 主图
        Map<String, Object> mainImagePart = new HashMap<>();
        mainImagePart.put("type", "image_url");
        Map<String, String> mainUrlMap = new HashMap<>();
        String dataUri = base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image;
        mainUrlMap.put("url", dataUri);
        mainImagePart.put("image_url", mainUrlMap);
        contentList.add(mainImagePart);

        // 衣柜图片
        if (wardrobeItems != null && !wardrobeItems.isEmpty()) {
            Map<String, Object> wardrobeTextPart = new HashMap<>();
            wardrobeTextPart.put("type", "text");
            wardrobeTextPart.put("text", "以下是我衣柜里的部分备选搭配单品(最多展示8件)：");
            contentList.add(wardrobeTextPart);

            int count = 0;
            for (com.campus.outfit.entity.WardrobeItem item : wardrobeItems) {
                if (count >= 8) break; // 限制最大参考单品数量防止 Token 溢出
                if (item.getOriginalImageUrl() != null && item.getOriginalImageUrl().startsWith("http")) {
                    try {
                        // 解决网络隔离：从本地或内网下载图片字节并转为 Base64
                        byte[] itemBytes = downloadImageBytes(item.getOriginalImageUrl());
                        String itemBase64 = java.util.Base64.getEncoder().encodeToString(itemBytes);

                        Map<String, Object> imgPart = new HashMap<>();
                        imgPart.put("type", "image_url");
                        Map<String, String> urlMap = new HashMap<>();
                        // 拼装符合大模型要求的 data URI
                        urlMap.put("url", "data:image/jpeg;base64," + itemBase64);
                        imgPart.put("image_url", urlMap);
                        contentList.add(imgPart);
                        
                        Map<String, Object> idPart = new HashMap<>();
                        idPart.put("type", "text");
                        idPart.put("text", "（单品 ID: " + item.getId() + "）");
                        contentList.add(idPart);
                        
                        count++;
                    } catch (Exception e) {
                        // 容错处理：如果单张图片下载/解码失败，跳过该单品，不影响整体穿搭分析
                        log.warn("[AI Recommend] 衣柜单品(ID:{})图片提取失败，已跳过。原因: {}", item.getId(), e.getMessage());
                    }
                }
            }
        }

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);

        // 3. 构建请求
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            
            // 存储到会话上下文 (只保存最后一条助手回复和之前的系统/用户消息，简化存储)
            List<Map<String, Object>> history = new ArrayList<>(messages);
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", content);
            history.add(assistantMsg);
            conversationContexts.put(sessionId, history);

            return extractJson(content);
        } catch (Exception e) {
            log.error("衣柜上下文分析失败: {}", e.getMessage());
            throw new com.campus.outfit.exception.BusinessException("AI 分析衣柜搭配失败，请检查图片是否合法或稍后重试: " + e.getMessage());
        }
    }

    @Override
    public String chatWithWardrobeContext(String sessionId, String message, List<WardrobeItem> wardrobeItems) {
        log.info("[AI Service] 衣柜聊天对话, sessionId: {}", sessionId);
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        List<Map<String, Object>> history = conversationContexts.get(sessionId);
        if (history == null) {
            // 如果会话不存在，初始化一个空会话或简单的系统提示
            history = new ArrayList<>();
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一位专业的校园穿搭顾问。请基于之前的分析和用户的询问提供穿搭建议。");
            history.add(systemMessage);
        }

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        history.add(userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", history);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String aiReply = root.path("choices").path(0).path("message").path("content").asText();

            // 更新历史记录
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiReply);
            history.add(assistantMsg);
            conversationContexts.put(sessionId, history);

            return aiReply;
        } catch (Exception e) {
            log.error("衣柜聊天失败: {}", e.getMessage());
            return "抱歉，穿搭顾问暂时无法响应，请稍后再试。";
        }
    }

    @Override
    public String generateImage(String prompt) {
        log.info("[AI Service] 开始通过 Seedream 生成图像, Prompt: {}", prompt);
        String url = "https://ark.cn-beijing.volces.com/api/v3/images/generations";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", imageModelId);
        requestBody.put("prompt", prompt);
        requestBody.put("size", "2K");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            
            if (root.has("error")) {
                throw new RuntimeException(root.path("error").path("message").asText());
            }

            String imageUrl = root.path("data").path(0).path("url").asText();
            log.info("[AI Service] 图像生成成功: {}", imageUrl);
            return imageUrl;
        } catch (Exception e) {
            log.error("图像生成失败: {}", e.getMessage());
            throw new RuntimeException("生成图像失败：" + e.getMessage());
        }
    }

    @Override
    public String analyzeWardrobeItem(String base64Image) {
        log.info("[AI Service] 开始分析单品图片并鉴定合法性...");

        // 1. 提取并校验 Base64 图片魔数（拦截非 JPG/PNG，特别是伪装的 WebP）
        try {
            String pureBase64 = base64Image;
            // 切除可能存在的 data:image/xxx;base64, 前缀
            if (base64Image.startsWith("data:")) {
                int commaIndex = base64Image.indexOf(",");
                if (commaIndex != -1) {
                    pureBase64 = base64Image.substring(commaIndex + 1);
                }
            }
            byte[] imageBytes = Base64.getDecoder().decode(pureBase64);
            
            if (imageBytes.length < 4) {
                throw new BusinessException("衣柜单品图片数据异常：文件过小！");
            }
            
            // 校验 Magic Number (魔数)
            boolean isJpeg = (imageBytes[0] == (byte)0xFF && imageBytes[1] == (byte)0xD8);
            boolean isPng = (imageBytes[0] == (byte)0x89 && imageBytes[1] == (byte)0x50 && imageBytes[2] == (byte)0x4E && imageBytes[3] == (byte)0x47);
            
            if (!isJpeg && !isPng) {
                String hexPrefix = String.format("%02X %02X %02X %02X", imageBytes[0], imageBytes[1], imageBytes[2], imageBytes[3]);
                log.error("[AI Service] 衣柜单品图片格式非法！文件头(Hex): {}", hexPrefix);
                throw new BusinessException("不支持的图片格式！请上传纯正的 JPG 或 PNG 图片 (系统拦截到未知文件头: " + hexPrefix + ")");
            }
        } catch (IllegalArgumentException e) {
            log.error("[AI Service] 衣柜单品 Base64 解码失败", e);
            throw new BusinessException("图片数据损坏，无法解码！");
        }
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        // 1. 系统提示词：极其严苛的单品鉴定逻辑
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个严苛的衣柜单品鉴定专家。用户会上传一张图片，你需要判断这是否是一张纯粹的【单件衣服/单品】图片。如果图片中包含人物穿着一套衣服（即穿搭）、或者同时出现了多件不同的衣服杂乱堆叠，请判定为非单品（isSingleItem 为 false）。\n" +
                "你必须严格输出以下 JSON 格式，不要包含任何 Markdown 标记或描述字句：\n" +
                "{\n" +
                "  \"isSingleItem\": true/false,\n" +
                "  \"categoryMain\": \"上装/下装/裙装/鞋靴/配饰等中的一个\",\n" +
                "  \"categorySub\": \"具体品类如卫衣/牛仔裤\",\n" +
                "  \"color\": \"最主要的一种颜色\",\n" +
                "  \"season\": \"春/夏/秋/冬中的一个或多个（以逗号分隔）\",\n" +
                "  \"material\": \"材质\",\n" +
                "  \"reason\": \"如果 isSingleItem 为 false，请在这里简短给出拒绝原因，比如：图中包含人物穿搭 / 包含多件衣服\"\n" +
                "}");

        // 2. 多模态 User Message
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "请鉴定并提取该单品的属性字段。");
        contentList.add(textPart);

        Map<String, Object> imgPart = new HashMap<>();
        imgPart.put("type", "image_url");
        Map<String, String> urlMap = new HashMap<>();
        // 自动补全 data URI 前缀以防万一
        String dataUri = base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image;
        urlMap.put("url", dataUri);
        imgPart.put("image_url", urlMap);
        contentList.add(imgPart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);

        // 3. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", Arrays.asList(systemMessage, userMessage));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            
            log.info("[AI Service] 单品鉴定原始回复: {}", content);
            return extractJson(content);
        } catch (Exception e) {
            log.error("单品鉴定失败: {}", e.getMessage());
            throw new RuntimeException("AI 鉴定单品服务异常", e);
        }
    }

    @Override
    public String analyzePortraitForTryOn(String base64Image) {
        log.info("[AI Service] 开始人像底图合规性审计...");

        // 1. 提取并校验 Base64 图片魔数（拦截非 JPG/PNG）
        try {
            String pureBase64 = base64Image;
            if (base64Image.startsWith("data:")) {
                int commaIndex = base64Image.indexOf(",");
                if (commaIndex != -1) {
                    pureBase64 = base64Image.substring(commaIndex + 1);
                }
            }
            byte[] imageBytes = Base64.getDecoder().decode(pureBase64);
            
            if (imageBytes.length < 4) {
                throw new BusinessException("人像审计图片数据异常：文件过小！");
            }
            
            // 校验 Magic Number
            boolean isJpeg = (imageBytes[0] == (byte)0xFF && imageBytes[1] == (byte)0xD8);
            boolean isPng = (imageBytes[0] == (byte)0x89 && imageBytes[1] == (byte)0x50 && imageBytes[2] == (byte)0x4E && imageBytes[3] == (byte)0x47);
            
            if (!isJpeg && !isPng) {
                String hexPrefix = String.format("%02X %02X %02X %02X", imageBytes[0], imageBytes[1], imageBytes[2], imageBytes[3]);
                log.error("[AI Service] 人像审计图片格式非法！文件头(Hex): {}", hexPrefix);
                throw new BusinessException("不支持的图片格式！请上传纯正的 JPG 或 PNG 作为人像底图 (拦截到未知文件头: " + hexPrefix + ")");
            }
        } catch (IllegalArgumentException e) {
            log.error("[AI Service] 人像审计 Base64 解码失败", e);
            throw new BusinessException("图片数据损坏，无法解码！");
        }
        String url = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(doubaoKey);

        // 1. 系统提示词 - 设定为审计专家
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个严苛的 AI 虚拟试衣审核专家。用户会上传一张照片，你需要判断它是否适合作为虚拟换装的模特底图。要求：1. 必须有且仅有1个人类；2. 必须是能够看清上身和下身的全身照或大半身照；3. 人物身体不能有严重的遮挡。请严格输出以下JSON格式：{\"isSuitable\": true/false, \"reason\": \"如果为false，请用一句话友善地告诉用户原因，比如：检测到多人、未检测到全身、身体被遮挡等\"}");

        // 2. 构建消息体
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "请审核这张人像照片是否符合虚拟试衣底图的标准。");
        contentList.add(textPart);

        Map<String, Object> imgPart = new HashMap<>();
        imgPart.put("type", "image_url");
        Map<String, String> urlMap = new HashMap<>();
        String dataUri = base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image;
        urlMap.put("url", dataUri);
        imgPart.put("image_url", urlMap);
        contentList.add(imgPart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", endpointId);
        requestBody.put("messages", Arrays.asList(systemMessage, userMessage));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            
            log.info("[AI Service] 人像审计原始回复: {}", content);
            return extractJson(content);
        } catch (Exception e) {
            log.error("人像审计服务异常: {}", e.getMessage());
            throw new RuntimeException("AI 人像审计服务暂时不可用", e);
        }
    }

}
