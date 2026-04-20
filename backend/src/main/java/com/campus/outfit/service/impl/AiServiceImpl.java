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
            // 【修改点 1】使用强制 JSON 模板约束 AI 输出，杜绝字段丢失
            String systemPrompt = "你是一个专业的校园穿搭分析助手。请严格分析图中衣物的材质和类型（如羽绒服代表冬季/冷，短袖代表夏季/热），并必须严格按照以下JSON格式输出结果，不要输出任何其他说明文字或Markdown标记：\n" +
                    "{\n" +
                    "  \"gender\": \"推断该衣物属于男款(1)、女款(2)还是中性款(0)，仅限整数: 1、2 或 0 其中之一\",\n" +
                    "  \"season\": \"推断该衣物适合穿的季节，仅限：春/夏/秋/冬/春秋其中之一\",\n" +
                    "  \"temperatureRange\": \"推断该衣物适合穿的温度，仅限：冷/凉/舒适/热其中之一\",\n" +
                    "  \"styleTags\": [\"风格标签1\", \"风格标签2\"],\n" +
                    "  \"colorTags\": [\"颜色标签1\", \"颜色标签2\"],\n" +
                    "  \"itemKeywords\": [\"识别到的单品1\", \"识别到的单品2\"],\n" +
                    "  \"suggestion\": \"一句话的穿搭建议或改进点\"\n" +
                    "}";

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
            AiAnalysisResult result = objectMapper.readValue(extractJson(aiResponse), AiAnalysisResult.class);
            
            // 【正则补强】如果 JSON 解析后的关键字段为空，尝试从原始文本中正则匹配提取
            if (!isValid(result.getSeason())) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"season\"\\s*:\\s*\"([^\"]+)\"").matcher(aiResponse);
                if (m.find()) result.setSeason(m.group(1));
            }
            if (!isValid(result.getTemperatureRange())) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"temperatureRange\"\\s*:\\s*\"([^\"]+)\"").matcher(aiResponse);
                if (m.find()) result.setTemperatureRange(m.group(1));
                else {
                    m = java.util.regex.Pattern.compile("\"temperature_range\"\\s*:\\s*\"([^\"]+)\"").matcher(aiResponse);
                    if (m.find()) result.setTemperatureRange(m.group(1));
                }
            }

            // 【深度清洗】防御性字段填充，强制排除 "null" 字符串
            if (result.getGender() == null) result.setGender(0);
            if (!isValid(result.getSeason())) result.setSeason("春秋"); 
            if (!isValid(result.getTemperatureRange())) result.setTemperatureRange("舒适");
            if (!isValid(result.getSuggestion())) result.setSuggestion("这套穿搭平衡感很好，适合多种日常校园场景。");
            
            return result;
        } catch (Exception e) {
            log.error("分析失败: {}", e.getMessage());
            AiAnalysisResult fallback = new AiAnalysisResult();
            fallback.setSeason("春秋");
            fallback.setTemperatureRange("舒适");
            fallback.setSuggestion("AI 正在开小差，但这套搭配看起来很有个性！");
            return fallback;
        }
    }

    private boolean isValid(String val) {
        return val != null && !val.trim().isEmpty() && !"null".equalsIgnoreCase(val) && !"undefined".equalsIgnoreCase(val);
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
    public String generateTryOnImage(String personImageUrl, List<String> outfitImageUrls) {
        // 目前由 DressingDiffusionV2 (Seedream) 逻辑接管
        log.info("[AI Service] 尝试进行虚拟试衣...");
        List<String> images = new ArrayList<>();
        images.add(personImageUrl);
        if (outfitImageUrls != null) {
            images.addAll(outfitImageUrls);
        }
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
        // 精准化提示词：限制分类和季节范围
        String systemPrompt = "你是一个专业的衣橱管理专家。请分析图中的单件衣物。要求：\n" +
                "1. 判定是否为单件衣物(isSingleItem)；\n" +
                "2. 将其分类到主类目(categoryMain)，必须且仅限为：“上装”或“下装”；\n" +
                "3. 识别其适合的季节(season)，必须且仅限为：“春/秋”、“夏”或“冬”之一；\n" +
                "4. 提取子类目(categorySub，如衬衫、牛仔裤)、颜色(color)、材质(material)。\n" +
                "请严格按以下JSON格式输出，不要有额外描述：\n" +
                "{\"isSingleItem\":true/false, \"categoryMain\":\"\", \"categorySub\":\"\", \"color\":\"\", \"season\":\"\", \"material\":\"\", \"reason\":\"\"}";
        
        messages.add(new DoubaoMessage("system", systemPrompt));
        
        DoubaoMessage userMsg = new DoubaoMessage();
        userMsg.setRole("user");
        List<DoubaoContentPart> parts = new ArrayList<>();
        parts.add(DoubaoContentPart.text("请鉴定此单品属性"));
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
        // 鲁棒性增强：清洗可能存在的 Markdown 标记，如 ```json 或 ```
        content = content.replace("```json", "").replace("```", "").trim();
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1) return content.substring(start, end + 1);
        return content.trim();
    }
}