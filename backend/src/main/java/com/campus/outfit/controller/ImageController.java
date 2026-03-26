package com.campus.outfit.controller;

import com.campus.outfit.service.MinioService;
import com.campus.outfit.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Autowired
    private MinioService minioService;

    /**
     * 根据对象名获取图片的预签名URL
     * @param objectName MinIO中的对象名
     * @return 有效的预签名URL
     */
    @GetMapping("/url")
    public Result<String> getImageUrl(@RequestParam String objectName) {
        try {
            String url = minioService.getImageUrl(objectName);
            return Result.success(url);
        } catch (Exception e) {
            return Result.fail("获取图片URL失败: " + e.getMessage());
        }
    }
}