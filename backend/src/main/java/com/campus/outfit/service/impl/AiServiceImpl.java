package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.service.AiService;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.DoubaoUtil.DoubaoMessage;
import com.campus.outfit.util.DoubaoUtil.DoubaoContentPart;
import com.campus.outfit.util.SeedreamUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${api.volcengine.ak:}")
    private String volcAk;

    @Value("${api.volcengine.sk:}")
    private String volcSk;

    private IVisualService visualService;

    @Autowired
    private DoubaoUtil doubaoUtil;

    @Autowired
    private SeedreamUtil seedreamUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CONTEXT_KEY_PREFIX = "ai:context:";
    private static final long EXPIRE_HOURS = 2;

    public AiServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(40000); 
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        log.info("[AI Service] 正在初始化视觉服务...");
        this.visualService = VisualServiceImpl.getInstance();
        this.visualService.setAccessKey(volcAk);
        this.visualService.setSecretKey(volcSk);
    }

    @Override
    public AiAnalysisResult analyzeOutfit(byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        try {
            List<DoubaoMessage> messages = new ArrayList<>();
            // 【修改点 1】增强系统提示词，明确规则和字段要求
            String systemPrompt = "你是一个专业的校园穿搭分析助手。请严格输出JSON格式的分析结果。" +
                    "请务必根据图中衣物的材质和类型（例如：羽绒服/厚毛衣代表冬季，短袖/短裤代表夏季，长袖衬衫/薄风衣代表春秋）推断并严格返回以下字段：\n" +
                    "1. \"season\": 适合的季节（仅限输出：'春', '夏', '秋', '冬', '春秋'）；\n" +
                    "2. \"temperatureRange\": 适合的温度感受（仅限输出：'冷', '凉', '舒适', '热'）；\n" +
                    "3. 其他字段需包含：styleTags(数组), colorTags(数组), itemKeywords(数组), suggestion(字符串)。";

            messages.add(new DoubaoMessage("system", systemPrompt));

            DoubaoMessage userMsg = new DoubaoMessage();
            userMsg.setRole("user");
            List<DoubaoContentPart> parts = new ArrayList<>();
            parts.add(DoubaoContentPart.text("请分析这张穿搭图片的风格、单品，并推断适合的季节和温度。"));
            String fmt = doubaoUtil.resolveAndFormatImage("data:image/jpeg;base64," + base64Image);
            if (fmt != null) parts.add(DoubaoContentPart.image(fmt));
            userMsg.setContent(parts);
            messages.add(userMsg);

            String aiResponse = doubaoUtil.chatWithVision(messages);
            return objectMapper.readValue(extractJson(aiResponse), AiAnalysisResult.class);
        } catch (Exception e) {
            log.error("分析失败: {}", e.getMessage());
            return new AiAnalysisResult();
        }
    }

    @Override
    public String analyzePortraitForTryOn(String base64Image) {
        List<DoubaoMessage> messages = new ArrayList<>();
        messages.add(new DoubaoMessage("system", "审核模特底图。JSON: isSuitable, reason。"));
        
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        List<DoubaoContentPart> parts = new ArrayList<>();
        parts.add(DoubaoContentPart.text("审核此模特图"));
        String fmt = doubaoUtil.resolveAndFormatImage(base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image);
        if (fmt != null) parts.add(DoubaoContentPart.image(fmt));
        userMsg.setContent(parts);
        messages.add(userMsg);

        return extractJson(doubaoUtil.chatWithVision(messages));
    }

    @Override
    public String generateTryOnImage(String personImageUrl, String outfitImageUrl) {
        // 目前由 DressingDiffusionV2 (Seedream) 逻辑接管
        log.info("[AI Service] 尝试进行虚拟试衣...");
        List<String> images = new ArrayList<>();
        images.add(personImageUrl);
        images.add(outfitImageUrl);
        return seedreamUtil.generateImageFromMultipleBase64("基于提供的素材生成试衣效果", images);
    }

    @Override
    public String analyzeOutfitWithWardrobe(String base64Image, Long userId, String sessionId, List<WardrobeItem> wardrobeItems) {
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;
        List<DoubaoMessage> messages = new ArrayList<>();
        messages.add(new DoubaoMessage("system", "分析主图建议搭配。JSON: style, items, occasion, suggestions, recommendations (含 id, reason)。"));
        
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        List<DoubaoContentPart> parts = new ArrayList<>();
        parts.add(DoubaoContentPart.text("基于这些图片进行穿搭分析"));
        
        String mainFmt = doubaoUtil.resolveAndFormatImage(base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image);
        if (mainFmt != null) parts.add(DoubaoContentPart.image(mainFmt));
        
        if (wardrobeItems != null) {
            for (WardrobeItem item : wardrobeItems) {
                if (item.getOriginalImageUrl() != null) {
                    String itemFmt = doubaoUtil.resolveAndFormatImage(item.getOriginalImageUrl());
                    if (itemFmt != null) parts.add(DoubaoContentPart.image(itemFmt));
                }
            }
        }
        userMsg.setContent(parts);
        messages.add(userMsg);

        String reply = doubaoUtil.chatWithVision(messages);
        saveHistory(redisKey, messages); 
        return extractJson(reply);
    }

    @Override
    public String chatWithWardrobeContext(String sessionId, String message, List<WardrobeItem> wardrobeItems) {
        String redisKey = CONTEXT_KEY_PREFIX + sessionId;
        List<DoubaoMessage> history = loadHistory(redisKey);
        
        if (history.isEmpty()) {
            history.add(new DoubaoMessage("system", "你是一位专业的校园穿搭顾问。"));
        }
        
        history.add(new DoubaoMessage("user", message));
        String reply = doubaoUtil.chatWithVision(history);
        history.add(new DoubaoMessage("assistant", reply));
        saveHistory(redisKey, history);
        return reply;
    }

    @Override
    public String generateImage(String prompt) {
        return seedreamUtil.generateImage(prompt, null);
    }

    @Override
    public String analyzeWardrobeItem(String base64Image) {
        List<DoubaoMessage> messages = new ArrayList<>();
        messages.add(new DoubaoMessage("system", "鉴定单件属性。JSON: isSingleItem, categoryMain, categorySub, color, season, material, reason。"));
        
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        List<DoubaoContentPart> parts = new ArrayList<>();
        parts.add(DoubaoContentPart.text("请鉴定此单品"));
        String fmt = doubaoUtil.resolveAndFormatImage(base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image);
        if (fmt != null) parts.add(DoubaoContentPart.image(fmt));
        userMsg.setContent(parts);
        messages.add(userMsg);

        return extractJson(doubaoUtil.chatWithVision(messages));
    }

    @Override
    public String generateImageFromMultipleBase64(String prompt, List<String> base64Images) {
        return seedreamUtil.generateImageFromMultipleBase64(prompt, base64Images);
    }

    private List<DoubaoMessage> loadHistory(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<DoubaoMessage>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveHistory(String key, List<DoubaoMessage> history) {
        try {
            if (history.size() > 20) {
                history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
            }
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(history), EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("[AI Service] 历史保存失败: {}", e.getMessage());
        }
    }

    private String extractJson(String content) {
        if (content == null) return "{}";
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1) return content.substring(start, end + 1);
        return content.trim();
    }
}