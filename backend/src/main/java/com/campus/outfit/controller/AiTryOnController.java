package com.campus.outfit.controller;

import com.campus.outfit.service.AiService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiTryOnController {

    @Autowired
    private AiService aiService;

    @PostMapping("/try-on")
    public Result<String> tryOnOutfit(@RequestBody Map<String, String> request) {
        try {
            String personImageUrl = request.get("personImageUrl");
            String outfitImageUrl = request.get("outfitImageUrl");
            
            if (personImageUrl == null || outfitImageUrl == null) {
                return Result.fail("人像图片和服装图片不能为空");
            }
            
            String resultUrl = aiService.generateTryOnImage(personImageUrl, outfitImageUrl);
            return Result.success(resultUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("换装处理失败：" + e.getMessage());
        }
    }
}
