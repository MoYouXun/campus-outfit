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
        // 提示词加强：显式定义 JSON 格式并加入【1个对象】的物理约束
        String prompt = "请分析这张穿搭主图，并从我的衣柜中挑选单品给出搭配建议。返回 JSON 格式结果，包含字段：style (整体风格), suggestions (建议列表), recommendations (单品列表，内部项含 title, desc)。" +
                       "【重要约束】必须且只能推荐 1 套最核心的穿搭搭配，recommendations 数组里面只能包含 1 个对象，严禁返回多个。";
        String aiJson = doubaoUtil.chat(userId, sessionId, prompt, base64Image);
        return enhanceJsonWithImage(aiJson, base64Image, userId);
    }

    @Override
    public String chat(String sessionId, String message, Long userId) {
        log.info("[AiAssistant] 执行穿搭对话, userId: {}, sessionId: {}", userId, sessionId);
        String aiReply = doubaoUtil.chat(userId, sessionId, message);
        
        // 关键逻辑：探测 AI 回复是否为结构化 JSON (针对特定追问场景)
        if (aiReply != null && aiReply.trim().startsWith("{") && aiReply.contains("\"recommendations\"")) {
            try {
                log.info("[AiAssistant] 检测到对话中包含穿搭建议 JSON，触发生图拦截器...");
                return enhanceJsonWithImage(aiReply, null, userId);
            } catch (Exception e) {
                log.error("[AiAssistant] 多轮对话生图增强失败，回退为原始回复: {}", e.getMessage());
            }
        }
        return aiReply;
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
            
            // 2. 图像上下文检索：如果当前调用没有主图 base64，尝试从历史记录中找回该用户的最后一张底图作为参考
            if (mainBase64 != null) {
                fusionBase64List.add(mainBase64);
            } else {
                List<AiAnalysisRecord> history = aiAnalysisRecordMapper.selectList(
                        new LambdaQueryWrapper<AiAnalysisRecord>()
                                .eq(AiAnalysisRecord::getUserId, userId)
                                .orderByDesc(AiAnalysisRecord::getCreateTime)
                                .last("LIMIT 1")
                );
                if (!history.isEmpty()) {
                    String lastImageUrl = history.get(0).getResultImageUrl();
                    if (lastImageUrl != null && !lastImageUrl.isEmpty()) {
                        log.info("[AiAssistant] 多轮对话中检索到历史底图，以此作为生图参考: {}", lastImageUrl);
                        fusionBase64List.add(lastImageUrl);
                    }
                }
            }

            // 3. 遍历建议单品，从用户衣柜寻找匹配的真实物品 (Seedream V2 常用模式为 [模特图, 服装图])
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

            // 4. 调用 Seedream 生成效果图并转存 MinIO
            if (fusionBase64List.size() >= 1) {
                try {
                    String promptText = root.path("style_name").asText("基于参考图进行穿搭设计");
                    log.info("[AiAssistant] 正在调用 Seedream 生成效果图, Prompt: {}, 参考图片张数: {}", promptText, fusionBase64List.size());
                    String tempResultUrl = seedreamUtil.generateImage(promptText, fusionBase64List);
                    
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
                        // 增加防御性截断：严格锁死只返回 1 个推荐，防止模型返回多项
                        while (recommendations.size() > 1) {
                            recommendations.remove(1);
                        }
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
