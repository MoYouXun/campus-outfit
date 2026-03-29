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

    /**
     * 批量上传并分析单品
     */
    @PostMapping("/batch-upload")
    public Result<java.util.List<WardrobeItem>> batchUpload(@RequestParam("files") MultipartFile[] files, 
                                                              @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(wardrobeItemService.uploadBatch(files, userId));
    }

    /**
     * 获取用户所有衣柜单品
     */
    @GetMapping("/list")
    public Result<java.util.List<WardrobeItem>> list(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(wardrobeItemService.getUserWardrobe(userId));
    }

    /**
     * 根据类目获取衣柜单品
     */
    @GetMapping("/listByType")
    public Result<java.util.List<WardrobeItem>> listByType(@RequestParam("type") String type, 
                                                           @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(wardrobeItemService.getWardrobeByType(userId, type));
    }

    /**
     * 根据季节获取衣柜单品
     */
    @GetMapping("/listBySeason")
    public Result<java.util.List<WardrobeItem>> listBySeason(@RequestParam("season") String season, 
                                                             @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(wardrobeItemService.getWardrobeBySeason(userId, season));
    }

    /**
     * 根据风格获取衣柜单品
     */
    @GetMapping("/listByStyle")
    public Result<java.util.List<WardrobeItem>> listByStyle(@RequestParam("style") String style, 
                                                            @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(wardrobeItemService.getWardrobeByStyle(userId, style));
    }

    /**
     * 删除衣柜单品
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, @RequestHeader("Authorization") String token) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        boolean success = wardrobeItemService.deleteWardrobeItem(id, userId);
        return success ? Result.success() : Result.fail("删除失败，单品不存在或无权限");
    }
}
