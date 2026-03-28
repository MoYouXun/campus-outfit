package com.campus.outfit.controller;

import com.campus.outfit.entity.WardrobeItem;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.service.WardrobeItemService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    private Long getUserId(String token) {
        return jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
    }

    /**
     * 上传单品并记录元数据
     */
    @PostMapping("/upload")
    public Result<WardrobeItem> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("type") String type,
                                       @RequestParam("color") String color,
                                       @RequestParam("style") String style,
                                       @RequestParam("season") String season,
                                       @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        WardrobeItem item = wardrobeItemService.uploadItem(file, userId, type, color, style, season);
        return Result.success(item);
    }

    /**
     * 获取当前用户的所有单品
     */
    @GetMapping("/list")
    public Result<List<WardrobeItem>> list(@RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        return Result.success(wardrobeItemService.getByUserId(userId));
    }

    /**
     * 根据类型筛选
     */
    @GetMapping("/listByType")
    public Result<List<WardrobeItem>> listByType(@RequestParam("type") String type,
                                                 @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        return Result.success(wardrobeItemService.getByType(userId, type));
    }

    /**
     * 根据季节筛选
     */
    @GetMapping("/listBySeason")
    public Result<List<WardrobeItem>> listBySeason(@RequestParam("season") String season,
                                                   @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        return Result.success(wardrobeItemService.getBySeason(userId, season));
    }

    /**
     * 根据风格筛选
     */
    @GetMapping("/listByStyle")
    public Result<List<WardrobeItem>> listByStyle(@RequestParam("style") String style,
                                                  @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        return Result.success(wardrobeItemService.getByStyle(userId, style));
    }

    /**
     * 获取单品详情
     */
    @GetMapping("/detail/{id}")
    public Result<WardrobeItem> detail(@PathVariable Long id) {
        WardrobeItem item = wardrobeItemService.getById(id);
        return item != null ? Result.success(item) : Result.fail("单品不存在");
    }

    /**
     * 删除单品
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean removed = wardrobeItemService.deleteItem(id);
        return removed ? Result.success(null) : Result.fail("删除失败，单品不存在或系统异常");
    }
}
