package com.campus.outfit.service.impl;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.service.AiService;
import com.campus.outfit.util.DoubaoUtil;
import com.campus.outfit.util.SeedreamUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(40000); // 同步调大超时至 40s
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
            String aiResponse = doubaoUtil.chat(0L, "analysis", "分析穿搭JSON", "data:image/jpeg;base64," + base64Image);
            return objectMapper.readValue(extractJson(aiResponse), AiAnalysisResult.class);
        } catch (Exception e) {
            log.error("分析失败: {}", e.getMessage());
            return new AiAnalysisResult();
        }
    }

    private byte[] downloadImageBytes(String imageUrl) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(java.net.URI.create(imageUrl), byte[].class);
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && !contentType.getType().toLowerCase().contains("image")) {
                log.warn("[AI Service] 跳过非图片响应: {}", imageUrl);
                return null;
            }
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.warn("[AI Service] 下载图片中断: {}", e.getMessage());
            return null; 
        }
    }

    @Override
    public String generateTryOnImage(String personImageUrl, String outfitImageUrl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String analyzeOutfitWithWardrobe(String base64Image, Long userId, String sessionId, List<WardrobeItem> wardrobeItems) {
        log.info("[AI Service] 全量分析中...");
        String prompt = "分析主图建议搭配。JSON: style, items, occasion, suggestions, recommendations (含 id, reason)。";
        List<String> contextImages = new ArrayList<>();
        contextImages.add(base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image);
        
        if (wardrobeItems != null) {
            for (WardrobeItem item : wardrobeItems) {
                if (item.getOriginalImageUrl() != null) {
                    byte[] bytes = downloadImageBytes(item.getOriginalImageUrl());
                    if (bytes != null) contextImages.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes));
                }
            }
        }
        return extractJson(doubaoUtil.chat(userId, sessionId, prompt, contextImages));
    }

    @Override
    public String chatWithWardrobeContext(String sessionId, String message, List<WardrobeItem> wardrobeItems) {
        return doubaoUtil.chat(0L, sessionId, message);
    }

    @Override
    public String generateImage(String prompt) {
        return seedreamUtil.generateImage(prompt, null);
    }

    @Override
    public String analyzeWardrobeItem(String base64Image) {
        String prompt = "鉴定单件属性。JSON: isSingleItem, categoryMain, categorySub, color, season, material, reason。";
        return extractJson(doubaoUtil.chat(0L, "wardrobe", prompt, base64Image));
    }

    @Override
    public String analyzePortraitForTryOn(String base64Image) {
        String prompt = "审核模特底图。JSON: isSuitable, reason。";
        return extractJson(doubaoUtil.chat(0L, "portrait", prompt, base64Image));
    }

    @Override
    public String generateImageFromMultipleBase64(String prompt, List<String> base64Images) {
        return seedreamUtil.generateImageFromMultipleBase64(prompt, base64Images);
    }

    private String extractJson(String content) {
        if (content == null) return "{}";
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1) return content.substring(start, end + 1);
        return content.trim();
    }
}