# 豆包AI穿搭分析模块代码汇总

## 一、后端代码

### 1. ImageController.java

路径：`Wear_project_demo/src/main/java/com/campus/outfit/controller/ImageController.java`

```java
package com.campus.outfit.controller;

import com.campus.outfit.dto.resp.Result;
import com.campus.outfit.entity.Clothing;
import com.campus.outfit.entity.Image;
import com.campus.outfit.mapper.ClothingMapper;
import com.campus.outfit.mapper.ImageMapper;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.MinioUtil;
import com.campus.outfit.util.SeedreamUtil;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final MinioUtil minioUtil;
    private final ImageMapper imageMapper;
    private final DoubaoUtil doubaoUtil;
    private final SeedreamUtil seedreamUtil;
    private final ClothingMapper clothingMapper;
    private final MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    public ImageController(MinioUtil minioUtil, ImageMapper imageMapper, DoubaoUtil doubaoUtil, SeedreamUtil seedreamUtil, ClothingMapper clothingMapper, MinioClient minioClient) {
        this.minioUtil = minioUtil;
        this.imageMapper = imageMapper;
        this.doubaoUtil = doubaoUtil;
        this.seedreamUtil = seedreamUtil;
        this.clothingMapper = clothingMapper;
        this.minioClient = minioClient;
    }

    /**
     * 从Minio获取图片并转为base64
     */
    private String getImageAsBase64(String objectName) throws Exception {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        )) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file) throws Exception {
        String fileName = minioUtil.upload(file);
        String url = minioUtil.getUrl(fileName);
        
        // 存储到数据库
        Image image = new Image();
        image.setFileName(fileName);
        image.setUserId(1); // 暂时使用默认用户ID，后续可从登录信息中获取
        imageMapper.insert(image);
        
        return Result.success("上传成功", Map.of("fileName", fileName, "url", url));
    }

    @PostMapping("/analyze")
    public Result analyze(@RequestBody Map<String, String> request) throws Exception {
        String fileName = request.get("fileName");
        String sessionId = request.get("sessionId");
        // 暂时使用默认用户ID 1，后续可从登录信息中获取
        Integer userId = 1;
        
        String analysisResult = doubaoUtil.analyzeImage(fileName, userId, sessionId);
        
        // 解析分析结果，为每个推荐生成图片
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(analysisResult);
        com.fasterxml.jackson.databind.JsonNode recommendations = root.path("recommendations");
        
        // 获取用户衣柜中的所有衣服
        List<Clothing> wardrobe = clothingMapper.selectByUserId(userId);
        
        if (recommendations.isArray()) {
            for (int i = 0; i < recommendations.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode recommendation = recommendations.get(i);
                String title = recommendation.path("title").asText();
                String desc = recommendation.path("desc").asText();
                com.fasterxml.jackson.databind.JsonNode itemsNode = recommendation.path("items");
                
                // 获取推荐单品的base64图片数据列表
                java.util.List<String> base64Images = new java.util.ArrayList<>();
                if (itemsNode.isArray()) {
                    for (int j = 0; j < itemsNode.size(); j++) {
                        String itemName = itemsNode.get(j).asText();
                        // 从衣柜中查找对应的衣服并获取图片
                        for (Clothing clothing : wardrobe) {
                            if (clothing.getFileName().equals(itemName)) {
                                try {
                                    String base64Image = getImageAsBase64("myclothes/" + clothing.getFileName());
                                    base64Images.add(base64Image);
                                } catch (Exception e) {
                                    System.err.println("获取图片base64失败: " + e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
                
                // 构建简洁的提示词，只要求生成穿搭效果图
                String prompt = "将这些衣服单品进行搭配，生成一套完整的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                
                // 使用多图生图功能生成图片
                String imageUrl;
                if (!base64Images.isEmpty()) {
                    imageUrl = seedreamUtil.generateImageFromMultipleBase64(prompt, base64Images, "2K");
                } else {
                    imageUrl = seedreamUtil.generateImage(prompt, "2K");
                }
                
                // 将图片URL添加到推荐数据中
                ((com.fasterxml.jackson.databind.node.ObjectNode) recommendation).put("image", imageUrl);
                
                // 替换单品列表为数据库中存储的style+color+description
                if (itemsNode.isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode newItemsNode = objectMapper.createArrayNode();
                    for (int j = 0; j < itemsNode.size(); j++) {
                        String itemName = itemsNode.get(j).asText();
                        // 从衣柜中查找对应的衣服
                        for (Clothing clothing : wardrobe) {
                            if (clothing.getFileName().equals(itemName)) {
                                // 构建style+color+description
                                String itemInfo = clothing.getStyle() + " " + clothing.getColor() + " " + (clothing.getDescription() != null ? clothing.getDescription() : "");
                                newItemsNode.add(itemInfo);
                                break;
                            }
                        }
                    }
                    // 替换原来的items节点
                    ((com.fasterxml.jackson.databind.node.ObjectNode) recommendation).set("items", newItemsNode);
                }
            }
        }
        
        // 将修改后的结果转换回JSON字符串
        String updatedResult = objectMapper.writeValueAsString(root);
        return Result.success("分析成功", Map.of("result", updatedResult));
    }
    
    /**
     * 测试图片生成接口
     */
    @GetMapping("/generate-image")
    public Result generateImage(@RequestParam String prompt) {
        try {
            String imageUrl = seedreamUtil.generateImage(prompt, "2048x1800");
            return Result.success("生成成功", Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            return Result.error("生成失败：" + e.getMessage());
        }
    }
    
    /**
     * 处理用户的后续消息，保持对话的连续性
     */
    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, String> request) throws Exception {
        String sessionId = request.get("sessionId");
        String message = request.get("message");
        // 暂时使用默认用户ID 1，后续可从登录信息中获取
        Integer userId = 1;
        
        String chatResult = doubaoUtil.chat(sessionId, message);
        
        // 解析豆包API返回的JSON结果
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(chatResult);
        com.fasterxml.jackson.databind.JsonNode recommendations = root.path("recommendations");
        
        // 获取用户衣柜中的所有衣服
        List<Clothing> wardrobe = clothingMapper.selectByUserId(userId);
        
        if (recommendations.isArray()) {
            for (int i = 0; i < recommendations.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode recommendation = recommendations.get(i);
                String title = recommendation.path("title").asText();
                String desc = recommendation.path("desc").asText();
                com.fasterxml.jackson.databind.JsonNode itemsNode = recommendation.path("items");
                
                // 获取推荐单品的base64图片数据列表
                java.util.List<String> base64Images = new java.util.ArrayList<>();
                if (itemsNode.isArray()) {
                    for (int j = 0; j < itemsNode.size(); j++) {
                        String itemName = itemsNode.get(j).asText();
                        // 从衣柜中查找对应的衣服并获取图片
                        for (Clothing clothing : wardrobe) {
                            if (clothing.getFileName().equals(itemName)) {
                                try {
                                    String base64Image = getImageAsBase64("myclothes/" + clothing.getFileName());
                                    base64Images.add(base64Image);
                                } catch (Exception e) {
                                    System.err.println("获取图片base64失败: " + e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }
                
                // 构建简洁的提示词，只要求生成穿搭效果图
                String prompt = "将这些衣服单品进行搭配，生成一套完整的穿搭效果图。保持衣服的原貌和特征，光线明亮，背景简洁，适合大学生日常穿搭。";
                
                // 使用多图生图功能生成图片
                String imageUrl;
                if (!base64Images.isEmpty()) {
                    imageUrl = seedreamUtil.generateImageFromMultipleBase64(prompt, base64Images, "2K");
                } else {
                    imageUrl = seedreamUtil.generateImage(prompt, "2K");
                }
                
                // 将图片URL添加到推荐数据中
                ((com.fasterxml.jackson.databind.node.ObjectNode) recommendation).put("image", imageUrl);
            }
        }
        
        // 将修改后的结果转换回JSON字符串
        String updatedResult = objectMapper.writeValueAsString(root);
        return Result.success("对话成功", Map.of("result", updatedResult));
    }
}
```

### 2. DoubaoUtil.java

路径：`Wear_project_demo/src/main/java/com/campus/outfit/util/DoubaoUtil.java`

```java
package com.campus.outfit.util;

import com.campus.outfit.entity.Clothing;
import com.campus.outfit.mapper.ClothingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DoubaoUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final ClothingMapper clothingMapper;

    @Value("${doubao.appId}")
    private String appId;

    @Value("${doubao.apiKey}")
    private String apiKey;

    @Value("${doubao.endpoint}")
    private String endpoint;

    @Value("${minio.bucketName}")
    private String bucketName;

    // 对话上下文管理，使用Map存储每个会话的历史消息
    private Map<String, List<Map<String, Object>>> conversationContexts = new HashMap<>();

    public DoubaoUtil(RestTemplate restTemplate, ObjectMapper objectMapper, MinioClient minioClient, ClothingMapper clothingMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.clothingMapper = clothingMapper;
    }

    public String analyzeImage(String fileName, Integer userId, String sessionId) {
        try {
            System.out.println("=== 开始分析图片 ===");
            System.out.println("文件名: " + fileName);
            System.out.println("用户ID: " + userId);
            System.out.println("会话ID: " + sessionId);
            
            // 从Minio获取上传的图片并转换为base64
            System.out.println("开始从Minio获取图片...");
            String base64Image = getImageAsBase64("myclothes/" + fileName);
            System.out.println("图片转换为base64成功，长度: " + (base64Image != null ? base64Image.length() : 0));
            
            // 获取用户衣柜中的所有衣服
            List<Clothing> wardrobe = clothingMapper.selectByUserId(userId);
            System.out.println("衣柜中衣服数量: " + wardrobe.size());
            
            // 构建提示词
            String prompt = buildPrompt(userId, wardrobe);
            System.out.println("提示词: " + prompt);
            
             // 使用公共网关地址（解决DNS解析失败问题）
            String apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
            System.out.println("API请求URL: " + apiUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            System.out.println("请求头设置完成");

            // 构建豆包API的请求体，使用base64作为data URL
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "ep-20260326150442-rt42w");
            
            // 构建消息内容，包含文本提示和所有图片
            List<Object> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", prompt));
            content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
            ));
            
            // 添加衣柜中其他衣服的图片
            for (Clothing clothing : wardrobe) {
                try {
                    String clothingBase64 = getImageAsBase64("myclothes/" + clothing.getFileName());
                    if (clothingBase64 != null && !clothingBase64.isEmpty()) {
                        content.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", "data:image/jpeg;base64," + clothingBase64)
                        ));
                    }
                } catch (Exception e) {
                    System.out.println("获取衣柜中衣服图片失败: " + clothing.getFileName() + ", 错误: " + e.getMessage());
                }
            }
            
            // 构建用户消息
            Map<String, Object> userMessage = Map.of(
                "role", "user", 
                "content", content
            );
            
            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(userMessage);
            
            // 保存对话上下文
            conversationContexts.put(sessionId, messages);
            
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);
            System.out.println("请求体构建完成，包含 " + content.size() + " 个内容项");

            
            // 测试网络连接
            System.out.println("测试网络连接...");
            try {
                java.net.InetAddress address = java.net.InetAddress.getByName("ark.cn-beijing.volces.com");
                System.out.println("DNS解析成功: " + address.getHostAddress());
            } catch (Exception e) {
                System.out.println("DNS解析失败: " + e.getMessage());
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String result = root.path("choices").get(0).path("message").path("content").asText();
                System.out.println("分析结果: " + result);
                
                // 将豆包的响应添加到对话上下文
                Map<String, Object> assistantMessage = Map.of(
                    "role", "assistant",
                    "content", result
                );
                // 直接使用之前定义的messages变量
                messages.add(assistantMessage);
                conversationContexts.put(sessionId, messages);
                
                return result;
            } else {
                throw new RuntimeException("豆包API调用失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("=== 错误信息 ===");
            System.out.println("错误类型: " + e.getClass().getName());
            System.out.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("调用豆包API时发生错误: " + e.getMessage(), e);
        }
    }

    private String getImageAsBase64(String objectName) throws Exception {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket("campus-outfit")
                        .object(objectName)
                        .build()
        )) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    private String buildPrompt(Integer userId, List<Clothing> wardrobe) {
        // 获取用户衣柜中的衣服信息
        String wardrobeInfo = getWardrobeInfo(userId);
        
        return "请仔细分析第一张图片中的衣服，并从后续提供的衣柜图片中选择合适的搭配，严格按照以下要求：\n" +
                "1. 首先分析第一张图片中的衣服类型、风格和颜色\n" +
                "2. 从衣柜图片中选择与第一张图片搭配的其他单品（如上衣、鞋子等）\n" +
                "3. 确保所有推荐的搭配都使用衣柜中实际存在的衣服图片\n" +
                "4. 给出以下信息：\n" +
                "   - 风格描述\n" +
                "   - 穿搭单品列表（只包含图片文件名，不包含文本描述）\n" +
                "   - 适合的场合\n" +
                "   - 推荐的改进建议\n" +
                "5. 基于用户衣柜中的衣服，生成1种最佳风格的穿搭推荐，包含：\n" +
                "   - 风格名称\n" +
                "   - 适合场景描述\n" +
                "   - 具体穿搭单品（必须从衣柜图片中选择，确保与第一张图片搭配协调季节一致，items列表中只包含衣服的文件名，不包含文本描述，必须与用户发的单品类型不同，并且必须包含用户发的单品）\n\n" +
                "用户衣柜中的衣服：\n" + wardrobeInfo + "\n\n" +
                "请用JSON格式返回结果，格式如下：\n" +
                "{\n" +
                "  \"style\": \"风格描述\",\n" +
                "  \"items\": [\"文件名1\", \"文件名2\", \"文件名3\"],\n" +
                "  \"occasion\": \"适合的场合\",\n" +
                "  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\"],\n" +
                "  \"recommendations\": [\n" +
                "    {\n" +
                "      \"title\": \"风格名称\",\n" +
                "      \"desc\": \"适合场景描述\",\n" +
                "      \"items\": [\"文件名1\", \"文件名2\", \"文件名3\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }
    
    private String getWardrobeInfo(Integer userId) {
        try {
            // 从数据库获取用户衣柜中的衣服
            List<Clothing> clothingList = clothingMapper.selectByUserId(userId);
            
            if (clothingList.isEmpty()) {
                return "用户衣柜为空";
            }
            
            StringBuilder wardrobeInfo = new StringBuilder();
            int count = 1;
            
            for (Clothing clothing : clothingList) {
                wardrobeInfo.append(count).append(". ")
                        .append(clothing.getFileName()).append(" - 类型：")
                        .append(clothing.getType()).append(" - 风格：")
                        .append(clothing.getStyle()).append(" - 颜色：")
                        .append(clothing.getColor()).append("\n");
                count++;
            }
            
            return wardrobeInfo.toString();
        } catch (Exception e) {
            System.out.println("获取衣柜信息失败: " + e.getMessage());
            // 如果获取失败，直接返回错误信息
            return "获取衣柜信息失败，请检查数据库连接或用户ID是否正确";
        }
    }
    
    /**
     * 处理用户的后续消息，保持对话的连续性
     * @param sessionId 会话ID
     * @param message 用户消息
     * @return 豆包的回复
     */
    public String chat(String sessionId, String message) {
        try {
            System.out.println("=== 开始处理对话 ===");
            System.out.println("会话ID: " + sessionId);
            System.out.println("用户消息: " + message);
            
            // 获取对话上下文
            List<Map<String, Object>> messages = conversationContexts.get(sessionId);
            if (messages == null) {
                // 如果没有上下文，创建一个新的
                messages = new ArrayList<>();
                conversationContexts.put(sessionId, messages);
            }
            
            // 添加用户消息到上下文
            Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", message
            );
            messages.add(userMessage);
            
             // 使用公共网关地址（解决DNS解析失败问题）
            String apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
            System.out.println("API请求URL: " + apiUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            System.out.println("请求头设置完成");

            // 构建豆包API的请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "ep-20260326150442-rt42w");
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);
            System.out.println("请求体构建完成，包含 " + messages.size() + " 条消息");

            // 测试网络连接
            System.out.println("测试网络连接...");
            try {
                java.net.InetAddress address = java.net.InetAddress.getByName("ark.cn-beijing.volces.com");
                System.out.println("DNS解析成功: " + address.getHostAddress());
            } catch (Exception e) {
                System.out.println("DNS解析失败: " + e.getMessage());
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String result = root.path("choices").get(0).path("message").path("content").asText();
                System.out.println("对话结果: " + result);
                
                // 将豆包的响应添加到对话上下文
                Map<String, Object> assistantMessage = Map.of(
                    "role", "assistant",
                    "content", result
                );
                messages.add(assistantMessage);
                conversationContexts.put(sessionId, messages);
                
                return result;
            } else {
                throw new RuntimeException("豆包API调用失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("=== 错误信息 ===");
            System.out.println("错误类型: " + e.getClass().getName());
            System.out.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("调用豆包API时发生错误: " + e.getMessage(), e);
        }
    }
}
```

### 3. SeedreamUtil.java

路径：`Wear_project_demo/src/main/java/com/campus/outfit/util/SeedreamUtil.java`

```java
package com.campus.outfit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SeedreamUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final String apiKey;
    private final String modelId;
    private final String endpoint;
    private final String bucketName;

    public SeedreamUtil(RestTemplate restTemplate, ObjectMapper objectMapper, MinioClient minioClient,
                       @Value("${ai.ark.api-key}") String apiKey, 
                       @Value("${ai.ark.model-id}") String modelId, 
                       @Value("${ai.ark.base-url}") String baseUrl,
                       @Value("${minio.bucketName}") String bucketName) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.apiKey = apiKey;
        this.modelId = modelId;
        this.endpoint = baseUrl + "/images/generations";
        this.bucketName = bucketName;
    }

    /**
     * 文生图：返回图片URL
     * @param prompt 提示词（中文）
     * @param size 尺寸：512x512 / 1024x1024 / 2K
     * @return 图片URL
     */
    public String generateImage(String prompt, String size) {
        System.out.println("=== 开始生成图片 ===");
        System.out.println("提示词: " + prompt);
        System.out.println("尺寸: " + size);
        System.out.println("API Key: " + apiKey);
        System.out.println("Model ID: " + modelId);
        System.out.println("Endpoint: " + endpoint);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("prompt", prompt);
            requestBody.put("size", size);
            requestBody.put("n", 1); // 生成1张

            System.out.println("请求体: " + objectMapper.writeValueAsString(requestBody));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                System.out.println("解析响应: " + root);
                
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    String imageUrl = data.get(0).path("url").asText();
                    System.out.println("生成的图片URL: " + imageUrl);
                    // 将图片下载并上传到Minio
                    String minioUrl = uploadImageToMinio(imageUrl);
                    System.out.println("Minio图片URL: " + minioUrl);
                    return minioUrl;
                } else {
                    System.err.println("生成图片失败：未返回图片URL");
                    throw new RuntimeException("生成图片失败：未返回图片URL");
                }
            } else {
                System.err.println("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
                throw new RuntimeException("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("=== 生成图片错误 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("生成图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 图生图：返回图片URL
     * @param prompt 提示词（中文）
     * @param imageUrl 原始图片URL
     * @param size 尺寸：512x512 / 1024x1024 / 2K
     * @return 图片URL
     */
    public String generateImageFromImage(String prompt, String imageUrl, String size) {
        System.out.println("=== 开始图生图 ===");
        System.out.println("提示词: " + prompt);
        System.out.println("原始图片URL: " + imageUrl);
        System.out.println("尺寸: " + size);
        System.out.println("API Key: " + apiKey);
        System.out.println("Model ID: " + modelId);
        System.out.println("Endpoint: " + endpoint);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("prompt", prompt);
            requestBody.put("image", imageUrl);
            requestBody.put("size", size);
            requestBody.put("watermark", false);

            System.out.println("请求体: " + objectMapper.writeValueAsString(requestBody));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                System.out.println("解析响应: " + root);
                
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    String generatedImageUrl = data.get(0).path("url").asText();
                    System.out.println("生成的图片URL: " + generatedImageUrl);
                    // 将图片下载并上传到Minio
                    String minioUrl = uploadImageToMinio(generatedImageUrl);
                    System.out.println("Minio图片URL: " + minioUrl);
                    return minioUrl;
                } else {
                    System.err.println("生成图片失败：未返回图片URL");
                    throw new RuntimeException("生成图片失败：未返回图片URL");
                }
            } else {
                System.err.println("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
                throw new RuntimeException("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("=== 生成图片错误 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("生成图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 图生图（使用base64图片数据）：返回图片URL
     * @param prompt 提示词（中文）
     * @param base64Image base64编码的图片数据
     * @param size 尺寸：512x512 / 1024x1024 / 2K
     * @return 图片URL
     */
    public String generateImageFromBase64(String prompt, String base64Image, String size) {
        System.out.println("=== 开始图生图（base64） ===");
        System.out.println("提示词: " + prompt);
        System.out.println("base64图片长度: " + (base64Image != null ? base64Image.length() : 0));
        System.out.println("尺寸: " + size);
        System.out.println("API Key: " + apiKey);
        System.out.println("Model ID: " + modelId);
        System.out.println("Endpoint: " + endpoint);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体，使用data URL格式
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("prompt", prompt);
            requestBody.put("image", "data:image/jpeg;base64," + base64Image);
            requestBody.put("size", size);
            requestBody.put("watermark", false);

            System.out.println("请求体: " + objectMapper.writeValueAsString(requestBody));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                System.out.println("解析响应: " + root);
                
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    String generatedImageUrl = data.get(0).path("url").asText();
                    System.out.println("生成的图片URL: " + generatedImageUrl);
                    // 将图片下载并上传到Minio
                    String minioUrl = uploadImageToMinio(generatedImageUrl);
                    System.out.println("Minio图片URL: " + minioUrl);
                    return minioUrl;
                } else {
                    System.err.println("生成图片失败：未返回图片URL");
                    throw new RuntimeException("生成图片失败：未返回图片URL");
                }
            } else {
                System.err.println("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
                throw new RuntimeException("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("=== 生成图片错误 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("生成图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 多图生图（使用base64图片数据）：返回图片URL
     * @param prompt 提示词（中文）
     * @param base64Images base64编码的图片数据列表
     * @param size 尺寸：512x512 / 1024x1024 / 2K
     * @return 图片URL
     */
    public String generateImageFromMultipleBase64(String prompt, java.util.List<String> base64Images, String size) {
        System.out.println("=== 开始多图生图（base64） ===");
        System.out.println("提示词: " + prompt);
        System.out.println("图片数量: " + (base64Images != null ? base64Images.size() : 0));
        System.out.println("尺寸: " + size);
        System.out.println("API Key: " + apiKey);
        System.out.println("Model ID: " + modelId);
        System.out.println("Endpoint: " + endpoint);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体，使用data URL格式
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("prompt", prompt);
            requestBody.put("size", size);
            requestBody.put("watermark", false);
            
            // 添加图片列表
            java.util.List<Object> images = new java.util.ArrayList<>();
            if (base64Images != null) {
                for (String base64Image : base64Images) {
                    images.add("data:image/jpeg;base64," + base64Image);
                }
            }
            requestBody.put("image", images);

            System.out.println("请求体: " + objectMapper.writeValueAsString(requestBody));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            System.out.println("开始发送API请求...");
            
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("API请求完成，状态码: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                System.out.println("解析响应: " + root);
                
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    String generatedImageUrl = data.get(0).path("url").asText();
                    System.out.println("生成的图片URL: " + generatedImageUrl);
                    // 将图片下载并上传到Minio
                    String minioUrl = uploadImageToMinio(generatedImageUrl);
                    System.out.println("Minio图片URL: " + minioUrl);
                    return minioUrl;
                } else {
                    System.err.println("生成图片失败：未返回图片URL");
                    throw new RuntimeException("生成图片失败：未返回图片URL");
                }
            } else {
                System.err.println("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
                throw new RuntimeException("生成图片失败：" + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("=== 生成图片错误 ===");
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误消息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("生成图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 下载图片并上传到Minio
     * @param imageUrl 图片URL
     * @return Minio中的图片URL
     */
    private String uploadImageToMinio(String imageUrl) throws Exception {
        // 下载图片
        byte[] imageBytes = downloadImage(imageUrl);
        
        // 上传到Minio
        String fileName = "generated_" + UUID.randomUUID() + ".jpg";
        String objectName = "generated/" + fileName;
        
        try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(in, imageBytes.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );
        }
        
        // 返回Minio的访问URL
        return minioClient.getPresignedObjectUrl(
                io.minio.GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(io.minio.http.Method.GET)
                        .build()
        );
    }

    /**
     * 下载图片
     * @param imageUrl 图片URL
     * @return 图片字节数组
     */
    private byte[] downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }
}
```

---

## 二、前端代码

### AiAnalysis/index.vue

路径：`Wear_project_forent/src/pages/AiAnalysis/index.vue`

```vue
<template>
  <div class="ai-analysis-container">
    <!-- 顶部导航栏 -->
    <div class="chat-header">
      <div class="header-left" @click="goBack">
        <span class="back-icon">←</span>
      </div>
      <div class="header-center">
        <h2>AI穿搭分析</h2>
        <p class="sub-title">智能穿搭助手</p>
      </div>
      <div class="header-right">
        <span class="more-icon">⋯</span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="chat-content" ref="chatContentRef">
      <!-- 欢迎消息 -->
      <div class="message-item ai-message">
        <div class="avatar">
          <img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cute%20robot%20avatar%20with%20blue%20background&image_size=square" alt="豆包" />
        </div>
        <div class="message-bubble">
          <p>你好！上传一张穿搭照片，选择季节和场合，我会为你分析并提供个性化的穿搭建议。</p>
        </div>
      </div>

      <!-- 对话历史记录 -->
      <div v-for="(message, index) in messages" :key="index" :class="['message-item', message.role === 'user' ? 'user-message' : 'ai-message']">
        <div class="avatar">
          <img :src="message.role === 'user' ? 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20profile%20picture&image_size=square' : 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cute%20robot%20avatar%20with%20blue%20background&image_size=square'" :alt="message.role === 'user' ? '用户' : '豆包'" />
        </div>
        <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : ''">
          <div v-if="message.type === 'text'">
            <p>{{ message.content }}</p>
          </div>
          <div v-else-if="message.type === 'image'" class="image-preview">
            <img :src="message.content" alt="图片" />
          </div>
          <div v-else-if="message.type === 'analysis'">
            <h4>分析结果</h4>
            <div class="result-content">
              <div class="result-item">
                <h5>风格</h5>
                <p>{{ message.content.style }}</p>
              </div>
              <div class="result-item">
                <h5>单品</h5>
                <p>{{ message.content.items.join(', ') }}</p>
              </div>
              <div class="result-item">
                <h5>适合场合</h5>
                <p>{{ message.content.occasion }}</p>
              </div>
              <div class="result-item" v-if="message.content.suggestions && message.content.suggestions.length > 0">
                <h5>改进建议</h5>
                <ul class="suggestions-list">
                  <li v-for="(suggestion, idx) in message.content.suggestions" :key="idx">{{ suggestion }}</li>
                </ul>
              </div>
            </div>

            <!-- 推荐搭配 -->
            <div class="recommend-section">
              <h4>推荐搭配</h4>
              <div class="recommend-list">
                <div v-for="(item, idx) in message.content.recommend || message.content.recommendations" :key="idx" class="recommend-item">
                  <img :src="item.image" class="recommend-img" />
                  <div class="recommend-body">
                    <h5>{{ item.title }}</h5>
                    <p>{{ item.desc }}</p>
                    <div v-if="item.items" class="recommend-items">
                      <span v-for="(product, id) in item.items" :key="id" class="item-tag">{{ product }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载中状态 -->
      <div v-if="loading" class="message-item ai-message">
        <div class="avatar">
          <img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=cute%20robot%20avatar%20with%20blue%20background&image_size=square" alt="豆包" />
        </div>
        <div class="message-bubble">
          <div class="loading">
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入区域 -->
    <div class="chat-input">
      <div class="input-header">
        <div class="user-info">
          <img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=user%20avatar%20profile%20picture&image_size=square" alt="用户" class="user-avatar" />
          <span class="user-name">@用户</span>
        </div>
      </div>
      <div class="input-content">
        <input 
          v-model="inputMessage" 
          type="text" 
          placeholder="输入消息..." 
          class="input-field"
          @keyup.enter="sendMessage"
        />
        <div class="input-tools">
          <button class="tool-button" title="选择衣柜中的图片" @click="openWardrobeDialog">
            <span class="tool-icon">+</span>
          </button>
          <button class="send-button" @click="analyze" :disabled="loading">
            <span class="send-icon">↑</span>
          </button>
        </div>
      </div>
      <div class="input-footer">
        <span class="input-hint">输入消息或上传图片进行穿搭分析</span>
      </div>
    </div>

    <!-- 衣柜选择对话框 -->
    <div v-if="showWardrobeDialog" class="wardrobe-dialog">
      <div class="dialog-content">
        <div class="dialog-header">
          <h3>选择衣柜中的图片</h3>
          <button class="close-button" @click="closeWardrobeDialog">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="wardrobeLoading" class="loading-container">
            <div class="loading">
              <span class="loading-dot"></span>
              <span class="loading-dot"></span>
              <span class="loading-dot"></span>
            </div>
            <p>加载中，请稍候...</p>
          </div>
          <div v-else-if="wardrobeItems.length === 0" class="empty-container">
            <p>衣柜中没有图片</p>
            <p class="empty-hint">请先添加衣服到衣柜</p>
          </div>
          <div v-else class="wardrobe-grid">
            <div 
              v-for="item in wardrobeItems" 
              :key="item.id" 
              class="wardrobe-item"
              @click="selectWardrobeItem(item)"
            >
              <div class="wardrobe-image-container">
                <img :src="item.url" alt="衣服" class="wardrobe-image" />
              </div>
              <div class="wardrobe-info">
                <p class="item-type">{{ item.type }}</p>
                <p class="item-style">{{ item.style }}</p>
                <p class="item-color" v-if="item.color">{{ item.color }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const fileList = ref([])
const imageUrl = ref('')
const loading = ref(false)
const analysisResult = ref(null)
const fileName = ref('')
const inputMessage = ref('')
const chatContentRef = ref(null)
const showWardrobeDialog = ref(false)
const wardrobeItems = ref([])
const wardrobeLoading = ref(false)
const messages = ref([])
const sessionId = ref(Date.now().toString()) // 用于标识对话会话

const form = reactive({
  season: '',
  occasion: ''
})

// 返回上一页
const goBack = () => {
  router.back()
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim()) return
  
  // 添加用户消息到对话历史
  const userMessage = {
    role: 'user',
    type: 'text',
    content: inputMessage.value
  }
  messages.value.push(userMessage)
  inputMessage.value = ''
  scrollToBottom()
  
  loading.value = true
  
  try {
    // 调用后端对话接口
    const response = await fetch('/api/image/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionId: sessionId.value,
        message: userMessage.content
      })
    })
    
    if (!response.ok) {
      throw new Error(`对话失败: ${response.status} ${response.statusText}`)
    }
    
    const data = await response.json()
    console.log('对话响应数据:', data)
    
    if (data.code === 200) {
      // 解析豆包API返回的JSON结果
      let analysisData
      try {
        analysisData = JSON.parse(data.data.result)
      } catch (e) {
        // 如果解析失败，使用默认格式
        analysisData = {
          style: '休闲校园风',
          items: ['白色T恤', '牛仔裤', '小白鞋', '棒球帽'],
          occasion: '上课',
          suggestions: ['建议1', '建议2', '建议3'],
          recommendations: [
            {
              title: '春季校园风',
              desc: '适合上课的舒适穿搭',
              items: ['白色T恤', '牛仔裤', '小白鞋'],
              image: imageUrl.value
            }
          ]
        }
      }
      
      // 添加AI回复到对话历史
      const aiMessage = {
        role: 'ai',
        type: 'analysis',
        content: {
          style: analysisData.style || '休闲校园风',
          items: analysisData.items || ['白色T恤', '牛仔裤', '小白鞋', '棒球帽'],
          occasion: analysisData.occasion || '上课',
          suggestions: analysisData.suggestions || ['建议1', '建议2', '建议3'],
          recommend: analysisData.recommendations ? analysisData.recommendations.map(item => ({
            title: item.title || '风格推荐',
            desc: item.desc || '适合场景',
            items: item.items || [],
            image: item.image || imageUrl.value
          })) : [
            {
              title: '春季校园风',
              desc: '适合上课的舒适穿搭',
              items: ['白色T恤', '牛仔裤', '小白鞋'],
              image: imageUrl.value
            }
          ],
          recommendations: analysisData.recommendations
        }
      }
      messages.value.push(aiMessage)
      loading.value = false
      scrollToBottom()
    } else {
      throw new Error(data.message || '对话失败')
    }
  } catch (error) {
    loading.value = false
    ElMessage.error('对话失败：' + error.message)
  }
}

// 打开衣柜选择对话框
const openWardrobeDialog = async () => {
  showWardrobeDialog.value = true
  wardrobeLoading.value = true
  
  try {
    // 调用后端接口获取衣柜中的图片列表，暂时使用默认用户ID 1
    const response = await fetch('/api/wardrobe/list?userId=1')
    
    if (!response.ok) {
      throw new Error(`获取衣柜图片失败: ${response.status} ${response.statusText}`)
    }
    
    const data = await response.json()
    console.log('衣柜图片列表:', data)
    
    if (data.code === 200) {
      wardrobeItems.value = data.data
    } else {
      throw new Error(data.message || '获取衣柜图片失败')
    }
  } catch (error) {
    console.error('获取衣柜图片失败:', error)
    ElMessage.error('获取衣柜图片失败：' + error.message)
  } finally {
    wardrobeLoading.value = false
  }
}

// 关闭衣柜选择对话框
const closeWardrobeDialog = () => {
  showWardrobeDialog.value = false
}

// 选择衣柜中的图片
const selectWardrobeItem = (item) => {
  fileName.value = item.fileName
  imageUrl.value = item.url
  showWardrobeDialog.value = false
  
  // 添加图片消息到对话历史
  const userMessage = {
    role: 'user',
    type: 'image',
    content: item.url
  }
  messages.value.push(userMessage)
  scrollToBottom()
  
  ElMessage.success('已选择衣柜中的图片')
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContentRef.value) {
      chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight
    }
  })
}

// 组件挂载后滚动到底部
onMounted(() => {
  scrollToBottom()
})

const handleFileChange = async (file) => {
  fileList.value = [file]
  // 生成预览 URL
  const reader = new FileReader()
  reader.readAsDataURL(file.raw)
  reader.onload = (e) => {
    imageUrl.value = e.target.result
  }
  
  // 选择图片时就上传到 Minio
  try {
    console.log('开始上传图片')
    const imageUrlFromServer = await uploadImage(file)
    console.log('上传成功，图片URL:', imageUrlFromServer)
  } catch (error) {
    console.error('上传错误:', error)
    ElMessage.error('上传失败：' + error.message)
  }
}

// 上传图片到后端
const uploadImage = async (file) => {
  const formData = new FormData()
  formData.append('file', file.raw)
  
  try {
    console.log('开始上传图片')
    const response = await fetch('/api/image/upload', {
      method: 'POST',
      body: formData
    })
    
    console.log('上传响应状态:', response.status)
    console.log('上传响应状态文本:', response.statusText)
    
    if (!response.ok) {
      throw new Error(`上传失败: ${response.status} ${response.statusText}`)
    }
    
    const data = await response.json()
    console.log('上传响应数据:', data)
    
    if (data.code === 200) {
      fileName.value = data.data.fileName
      return data.data.url
    } else {
      throw new Error(data.message || '上传失败')
    }
  } catch (error) {
    console.error('上传错误:', error)
    ElMessage.error('上传失败：' + error.message)
    throw error
  }
}

// 分析穿搭
const analyze = async () => {
  if (!imageUrl.value) {
    ElMessage.warning('请先上传图片')
    return
  }

  loading.value = true
  
  try {
    // 调用后端豆包API分析接口
    const response = await fetch('/api/image/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        fileName: fileName.value,
        sessionId: sessionId.value
      })
    })
    
    if (!response.ok) {
      throw new Error(`分析失败: ${response.status} ${response.statusText}`)
    }
    
    const data = await response.json()
    console.log('分析响应数据:', data)
    
    if (data.code === 200) {
      // 解析豆包API返回的JSON结果
      let analysisData
      try {
        analysisData = JSON.parse(data.data.result)
      } catch (e) {
        // 如果解析失败，使用默认格式
        analysisData = {
          style: '休闲校园风',
          items: ['白色T恤', '牛仔裤', '小白鞋', '棒球帽'],
          occasion: '上课',
          suggestions: ['建议1', '建议2', '建议3'],
          recommendations: [
            {
              title: '春季校园风',
              desc: '适合上课的舒适穿搭',
              items: ['白色T恤', '牛仔裤', '小白鞋'],
              image: imageUrl.value
            }
          ]
        }
      }
      
      // 添加分析结果到对话历史
      const aiMessage = {
        role: 'ai',
        type: 'analysis',
        content: {
          style: analysisData.style || '休闲校园风',
          items: analysisData.items || ['白色T恤', '牛仔裤', '小白鞋', '棒球帽'],
          occasion: analysisData.occasion || '上课',
          suggestions: analysisData.suggestions || ['建议1', '建议2', '建议3'],
          recommend: analysisData.recommendations ? analysisData.recommendations.map(item => ({
            title: item.title || '风格推荐',
            desc: item.desc || '适合场景',
            items: item.items || [],
            image: item.image || imageUrl.value
          })) : [
            {
              title: '春季校园风',
              desc: '适合上课的舒适穿搭',
              items: ['白色T恤', '牛仔裤', '小白鞋'],
              image: imageUrl.value
            }
          ],
          recommendations: analysisData.recommendations
        }
      }
      messages.value.push(aiMessage)
      loading.value = false
      scrollToBottom()
      ElMessage.success('分析完成')
    } else {
      throw new Error(data.message || '分析失败')
    }
  } catch (error) {
    loading.value = false
    ElMessage.error('分析失败：' + error.message)
  }
}
</script>

<style scoped>
.ai-analysis-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

/* 顶部导航栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background-color: #ffffff;
  border-bottom: 1px solid #e0e0e0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header-left {
  width: 40px;
}

.back-icon {
  font-size: 20px;
  cursor: pointer;
  color: #333;
}

.header-center {
  flex: 1;
  text-align: center;
}

.header-center h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.sub-title {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #999;
}

.header-right {
  width: 40px;
  text-align: right;
}

.more-icon {
  font-size: 20px;
  cursor: pointer;
  color: #333;
}

/* 聊天内容区域 */
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.user-message {
  flex-direction: row-reverse;
  position: relative;
}

.user-message .avatar {
  position: absolute;
  top: 0;
  right: 0;
  margin-right: 12px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 18px;
  line-height: 1.4;
}

.ai-bubble {
  background-color: #ffffff;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.user-bubble {
  background-color: #007aff;
  color: #ffffff;
  border-bottom-right-radius: 4px;
  padding-right: 52px; /* 为右上角的头像留出空间 */
  position: relative;
}

.message-bubble p {
  margin: 0;
  word-wrap: break-word;
}

/* 上传区域 */
.upload-section {
  margin-bottom: 0;
}

.image-preview {
  margin-top: 16px;
  max-width: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.image-preview img {
  width: 100%;
  height: auto;
  border-radius: 8px;
}

/* 表单样式 */
.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
}

.form-select {
  width: 100%;
  color: #333;
}

.analyze-button {
  margin-top: 16px;
  width: 100%;
}

/* 分析结果 */
.result-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 16px 0;
}

.result-item {
  padding: 12px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.result-item h5 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #666;
}

.result-item p {
  margin: 0;
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.suggestions-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
}

.suggestions-list li {
  margin-bottom: 4px;
  font-size: 14px;
  color: #666;
  line-height: 1.4;
}

/* 推荐搭配 */
.recommend-section {
  margin-top: 24px;
}

.recommend-section h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recommend-item {
  display: flex;
  gap: 12px;
  background-color: #f9f9f9;
  border-radius: 8px;
  padding: 12px;
}

.recommend-img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.recommend-body {
  flex: 1;
}

.recommend-body h5 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.recommend-body p {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #666;
  line-height: 1.4;
}

.recommend-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.item-tag {
  background-color: #ffffff;
  color: #666;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 11px;
  border: 1px solid #e0e0e0;
}

/* 加载中状态 */
.loading {
  display: flex;
  gap: 8px;
  align-items: center;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background-color: #007aff;
  border-radius: 50%;
  animation: pulse 1.5s infinite ease-in-out;
}

.loading-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  50% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 滚动条样式 */
.chat-content::-webkit-scrollbar {
  width: 6px;
}

.chat-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.chat-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.chat-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 底部输入区域 */
.chat-input {
  background-color: #f9f9f9;
  border-top: 1px solid #e0e0e0;
  padding: 12px 16px;
}

.input-header {
  margin-bottom: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.input-content {
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 24px;
  padding: 8px 12px;
}

.input-field {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  padding: 8px 0;
}

.input-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-button {
  width: 32px;
  height: 32px;
  border: none;
  background-color: transparent;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.tool-button:hover {
  background-color: #f0f0f0;
}

.tool-icon {
  font-size: 16px;
}

.send-button {
  width: 32px;
  height: 32px;
  border: none;
  background-color: #007aff;
  color: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.send-button:hover:not(:disabled) {
  background-color: #0056b3;
}

.send-button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.send-icon {
  font-size: 16px;
  font-weight: bold;
}

.input-footer {
  margin-top: 8px;
}

.input-hint {
  font-size: 12px;
  color: #999;
}

.upload-demo {
  margin: 0;
}

/* 衣柜选择对话框 */
.wardrobe-dialog {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background-color: #ffffff;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.close-button {
  width: 32px;
  height: 32px;
  border: none;
  background-color: transparent;
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.3s ease;
}

.close-button:hover {
  background-color: #f0f0f0;
}

.dialog-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #999;
  gap: 8px;
}

.empty-hint {
  font-size: 12px;
  color: #ccc;
  margin: 0;
}

.wardrobe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
}

.wardrobe-item {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.wardrobe-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  border-color: #007aff;
}

.wardrobe-image-container {
  position: relative;
  width: 100%;
  padding-bottom: 100%; /* 1:1 aspect ratio */
  overflow: hidden;
}

.wardrobe-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.wardrobe-item:hover .wardrobe-image {
  transform: scale(1.05);
}

.wardrobe-info {
  padding: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-type {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.item-style {
  margin: 0;
  font-size: 12px;
  color: #666;
}

.item-color {
  margin: 0;
  font-size: 11px;
  color: #999;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message-bubble {
    max-width: 85%;
  }
  
  .recommend-item {
    flex-direction: column;
  }
  
  .recommend-img {
    width: 100%;
    height: 150px;
  }
  
  .wardrobe-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
  
  .wardrobe-image-container {
    padding-bottom: 100%; /* 1:1 aspect ratio */
  }
  
  .wardrobe-image {
    height: 100%;
  }
}
</style>
```

---

## 三、配置文件

### application.yml

```yaml
spring:
  application:
    name: wear-project
  datasource:
    url: jdbc:mysql://localhost:3306/campus_outfit?useSSL=false&serverTimezone=UTC
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.campus.outfit.entity

server:
  port: 8082

minio:
  endpoint: http://localhost:9000
  accessKey: minioadmin
  secretKey: minioadmin
  bucketName: campus-outfit

doubao:
  appId: ep-20260326150442-rt42w
  apiKey: 7d9891f6-88d0-4935-9b81-29290ed453b7
  endpoint: https://ep-20260326150442-rt42w.cn-beijing.volces.com/api/v3

# AI - 火山方舟 Seedream4.5
ai:
  ark:
    api-key: 204abc9a-2a92-4c46-8e7a-3e6bf00a744c
    base-url: https://ark.cn-beijing.volces.com/api/v3
    model-id: doubao-seedream-4-5-251128
```

---

## 四、API接口说明

### 1. 图片上传
- **URL**: `/api/image/upload`
- **Method**: POST
- **Content-Type**: multipart/form-data
- **参数**: file (图片文件)
- **返回**: 上传成功的文件信息和URL

### 2. 穿搭分析
- **URL**: `/api/image/analyze`
- **Method**: POST
- **Content-Type**: application/json
- **参数**: 
  - `fileName`: 上传的图片文件名
  - `sessionId`: 会话ID
- **返回**: 分析结果和穿搭推荐

### 3. 对话聊天
- **URL**: `/api/image/chat`
- **Method**: POST
- **Content-Type**: application/json
- **参数**:
  - `sessionId`: 会话ID
  - `message`: 用户消息
- **返回**: 豆包的回复和新的穿搭推荐

### 4. 测试图片生成
- **URL**: `/api/image/generate-image`
- **Method**: GET
- **参数**: prompt (提示词)
- **返回**: 生成的图片URL

---

## 五、功能说明

### 主要功能
1. **图片上传**: 用户上传衣服图片到Minio存储
2. **穿搭分析**: 使用豆包AI分析衣服，从用户衣柜中选择搭配单品
3. **多图生图**: 使用豆包Seedream API根据多个单品图片生成穿搭效果图
4. **对话功能**: 用户可以与AI进行对话，提出修改意见获得新的推荐
5. **衣柜选择**: 用户可以从衣柜中选择衣服进行分析

### 技术特点
- 使用base64编码直接传递图片数据，避免URL访问问题
- 支持多图生图，同时传入2-14张图片生成穿搭效果图
- 对话上下文管理，保持对话连续性
- 响应式设计，支持移动端和桌面端
