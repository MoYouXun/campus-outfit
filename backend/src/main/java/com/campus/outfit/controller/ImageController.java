package com.campus.outfit.controller;


import com.campus.outfit.service.AiService;
import com.campus.outfit.service.MinioService;
import com.campus.outfit.utils.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Autowired
    private MinioService minioService;

    @Autowired
    private AiService aiService;

    @Autowired
    private ObjectMapper objectMapper;

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

    /**
     * 上传试穿人像底图并进行 AI 预审
     * @param file 人像照片
     * @return 预审通过的照片 URL
     */
    @PostMapping("/upload-portrait")
    public Result uploadPortrait(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 读取文件的字节流并转为 Base64 (AI 审核需要)
            byte[] fileBytes = file.getBytes();
            String base64Image = "data:" + file.getContentType() + ";base64," + Base64.getEncoder().encodeToString(fileBytes);

            // 2. 调用 AI 进行人像合规性预审 (先过审再上传)
            String jsonResult = aiService.analyzePortraitForTryOn(base64Image);
            JsonNode auditNode = objectMapper.readTree(jsonResult);

            boolean isSuitable = auditNode.path("isSuitable").asBoolean(false);
            String reason = auditNode.path("reason").asText("未说明原因");

            if (!isSuitable) {
                return Result.fail("上传失败：" + reason);
            }

            // 3. 预审通过后，进行物理存储至 MinIO
            String objectName = minioService.uploadImage(file);
            String imageUrl = minioService.getImageUrl(objectName);

            log.info("[AI 审计] 人像图片审核通过并成功上传: {}", objectName);
            return Result.success(Map.of("url", imageUrl));

        } catch (Exception e) {
            log.error("[AI 审计] 上传或预审人像底图系统异常: {}", e.getMessage(), e);
            return Result.fail("系统异常：" + e.getMessage());
        }
    }
}