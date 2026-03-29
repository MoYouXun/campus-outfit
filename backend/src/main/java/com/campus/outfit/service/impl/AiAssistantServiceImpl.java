package com.campus.outfit.service.impl;

import com.campus.outfit.entity.AiAnalysisRecord;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.mapper.AiAnalysisRecordMapper;
import com.campus.outfit.mapper.WardrobeItemMapper;
import com.campus.outfit.service.AiAssistantService;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.SeedreamUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 穿搭助手服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final DoubaoUtil doubaoUtil;
    private final SeedreamUtil seedreamUtil;
    private final MinioService minioService;
    private final WardrobeItemMapper wardrobeItemMapper;
    private final AiAnalysisRecordMapper aiAnalysisRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String analyze(String base64Image, String sessionId, Long userId) {
        log.info("[AiAssistant] 执行穿搭分析, userId: {}, sessionId: {}", userId, sessionId);
        String prompt = "请分析这张穿搭主图，并从我的衣柜中挑选单品给出搭配建议。";
        String aiJson = doubaoUtil.chat(userId, sessionId, prompt, base64Image);
        return enhanceJsonWithImage(aiJson, base64Image, userId);
    }

    @Override
    public String chat(String sessionId, String message, Long userId) {
        log.info("[AiAssistant] 执行穿搭对话, userId: {}, sessionId: {}", userId, sessionId);
        String aiJson = doubaoUtil.chat(userId, sessionId, message);
        return enhanceJsonWithImage(aiJson, null, userId);
    }

    /**
     * 增强 AI 返回的 JSON：提取建议单品 -> 搜索本地衣柜 -> 融合生图 -> 回填 URL
     */
    private String enhanceJsonWithImage(String aiJson, String mainBase64, Long userId) {
        try {
            // 1. 提取 JSON 纯净内容 (处理 Markdown 代码块)
            String cleanJson = extractJson(aiJson);
            ObjectNode root = (ObjectNode) objectMapper.readTree(cleanJson);
            
            if (!root.has("recommendations") || !root.get("recommendations").isArray()) {
                return cleanJson;
            }

            ArrayNode recommendations = (ArrayNode) root.get("recommendations");
            List<String> fusionBase64List = new ArrayList<>();
            if (mainBase64 != null) {
                fusionBase64List.add(mainBase64);
            }

            // 2. 遍历建议单品，从用户衣柜寻找匹配的真实物品 (Seedream V2 通常仅支持 1个模特+1个服装)
            List<Long> matchedItemIds = new ArrayList<>();
            for (JsonNode rec : recommendations) {
                String itemName = rec.path("title").asText();
                if (itemName == null || itemName.isEmpty()) continue;

                // 模糊查询：匹配 categorySub 或 categoryMain
                List<WardrobeItem> localItems = wardrobeItemMapper.selectList(
                        new LambdaQueryWrapper<WardrobeItem>()
                                .eq(WardrobeItem::getUserId, userId)
                                .and(w -> w.like(WardrobeItem::getCategorySub, itemName)
                                        .or().like(WardrobeItem::getCategoryMain, itemName))
                                .last("LIMIT 1")
                );

                if (!localItems.isEmpty()) {
                    WardrobeItem item = localItems.get(0);
                    log.info("[AiAssistant] 发现匹配单品: {} (ID: {})", itemName, item.getId());
                    
                    try (java.io.InputStream is = java.net.URI.create(item.getOriginalImageUrl()).toURL().openStream()) {
                        byte[] itemBytes = is.readAllBytes();
                        if (itemBytes != null) {
                            String itemBase64 = java.util.Base64.getEncoder().encodeToString(itemBytes);
                            fusionBase64List.add(itemBase64);
                            matchedItemIds.add(item.getId());
                            
                            // 关键：dressing_diffusionV2 常用模式为 [模特图, 服装图]
                            // 如果已经凑够了 2 张图，则停止，防止多图导致 Input invalid 错误
                            if (fusionBase64List.size() >= 2) break;
                        }
                    } catch (Exception e) {
                        log.warn("[AiAssistant] 提取衣柜单品 (ID: {}) 的图片失败: {}", item.getId(), e.getMessage());
                    }
                }
            }

            // 3. 调用 Seedream 生成效果图并转存 MinIO
            if (fusionBase64List.size() >= 1) {
                try {
                    log.info("[AiAssistant] 正在调用 Seedream 生成效果图，输入图片张数: {}", fusionBase64List.size());
                    String tempResultUrl = seedreamUtil.generateImageFromMultipleBase64(fusionBase64List);
                    
                    // 将临时结果转传至 MinIO 持久化存储
                    String objectName = minioService.uploadImageFromUrl(tempResultUrl);
                    String permanentUrl = minioService.getImageUrl(objectName);
                    log.info("[AiAssistant] 效果图已永久存储至 MinIO: {}", permanentUrl);

                    // 4. 保存分析记录至数据库 (参考衣柜记录汇总)
                    AiAnalysisRecord record = new AiAnalysisRecord();
                    record.setUserId(userId);
                    record.setStyleName(root.path("style_name").asText("未定义风格"));
                    
                    // 存储匹配到的单品 ID 列表为 JSON 字符串
                    String itemIdsJson = matchedItemIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",", "[", "]"));
                    record.setItemIds(itemIdsJson);

                    record.setResultImageUrl(permanentUrl);
                    record.setRawResultJson(root.toString());
                    aiAnalysisRecordMapper.insert(record);
                    log.info("[AiAssistant] 已保存 AI 穿搭分析记录, ID: {}", record.getId());

                    // 将永久 URL 回填到 JSON 结果中
                    root.put("image", permanentUrl);
                    if (recommendations.size() > 0) {
                        ((ObjectNode) recommendations.get(0)).put("image", permanentUrl);
                    }
                } catch (Exception e) {
                    log.error("[AiAssistant] Seedream 生图或持久化失败: {}", e.getMessage(), e);
                }
            }

            return root.toString();

        } catch (Exception e) {
            log.error("[AiAssistant] 流程编排失败: {}", e.getMessage(), e);
            throw new BusinessException("AI 助手处理异常：" + e.getMessage());
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1) {
            return content.substring(start, end + 1);
        }
        return content.trim();
    }
}
