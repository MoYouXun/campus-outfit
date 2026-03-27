package com.campus.outfit.controller;

import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.WardrobeItemService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 电子衣橱 API 控制器
 */
@RestController
@RequestMapping("/api/wardrobe")
public class WardrobeItemController {

    @Autowired
    private WardrobeItemService wardrobeItemService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 上传单品并进行 AI 分析
     */
    @PostMapping("/upload")
    public Result<WardrobeItem> upload(@RequestParam("file") MultipartFile file, 
                                       @RequestHeader("Authorization") String token) {
        // 从 Token 解析当前登录用户 ID
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        
        // 调用 Service 层业务逻辑
        WardrobeItem item = wardrobeItemService.uploadAndAnalyzeItem(file, userId);
        
        return Result.success(item);
    }
}
