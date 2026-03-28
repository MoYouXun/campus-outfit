package com.campus.outfit.utils;

import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.MinioService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 豆包 AI 分析工具类
 */
@Component
public class DoubaoUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;
    private final WardrobeItemMapper wardrobeItemMapper;

    @Value("${api.doubao.key}")
    private String apiKey;

    // 豆包 API 节点（建议从配置文件读取，默认为火山方舟通用节点）
    private final String apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

    public DoubaoUtil(RestTemplate restTemplate, ObjectMapper objectMapper, MinioService minioService, WardrobeItemMapper wardrobeItemMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.minioService = minioService;
        this.wardrobeItemMapper = wardrobeItemMapper;
    }

    /**
     * 分析图片并与衣柜匹配推荐
     * @param targetImageBase64 当前上传穿搭的 Base64
     * @param userId 用户 ID
     * @return 豆包返回的推荐 JSON
     */
    public String analyzeWithWardrobe(String targetImageBase64, Long userId) {
        try {
            // 1. 获取用户衣柜清单
            List<WardrobeItem> wardrobe = wardrobeItemMapper.selectList(
                    new LambdaQueryWrapper<WardrobeItem>().eq(WardrobeItem::getUserId, userId)
            );

            // 2. 构建 Prompt
            StringBuilder wardrobeInfo = new StringBuilder();
            if (wardrobe.isEmpty()) {
                wardrobeInfo.append("衣柜目前为空");
            } else {
                for (int i = 0; i < wardrobe.size(); i++) {
                    WardrobeItem item = wardrobe.get(i);
                    wardrobeInfo.append(i + 1).append(". ")
                            .append("分类:").append(item.getCategoryMain()).append(", ")
                            .append("风格:").append(item.getStyle()).append(", ")
                            .append("颜色:").append(item.getColor()).append(", ")
                            .append("文件标识:").append(item.getObjectName()).append("\n");
                }
            }

            String prompt = "请分析第一张图片（用户当前穿搭），并从我的衣柜清单中挑选最合适的 1-3 件单品进行搭配补全。\n" +
                    "我的衣柜清单如下：\n" + wardrobeInfo.toString() + "\n\n" +
                    "请以 JSON 格式严格返回以下字段：\n" +
                    "{\n" +
                    "  \"reasoning\": \"详细的专业穿搭建议（含推荐理由）\",\n" +
                    "  \"recommendedItems\": [\"选中单品的文件标识\"],\n" +
                    "  \"styleType\": \"整体风格名称\",\n" +
                    "  \"occasion\": \"建议场合\"\n" +
                    "}";

            // 3. 组装消息列表（含图片多模态）
            List<Object> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", prompt));
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:image/jpeg;base64," + targetImageBase64)
            ));

            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "doubao-vision-pro-240828"); // 使用支持视觉识别的模型节点
            requestBody.put("messages", List.of(Map.of("role", "user", "content", content)));
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText();
            } else {
                throw new RuntimeException("豆包 API 调用失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("豆包分析过程出错: " + e.getMessage());
        }
    }
}
