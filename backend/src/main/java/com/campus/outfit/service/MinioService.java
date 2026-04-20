package com.campus.outfit.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioService {

    @Value("${spring.minio.endpoint}")
    private String endpoint;

    @Value("${spring.minio.accessKey}")
    private String accessKey;

    @Value("${spring.minio.secretKey}")
    private String secretKey;

    @Value("${spring.minio.bucketName}")
    private String bucketName;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        System.out.println("[MinIO] 初始化MinIO客户端...");
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        
        // 检查并创建bucket
        try {
            System.out.println("[MinIO] 检查bucket是否存在: " + bucketName);
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                System.out.println("[MinIO] bucket不存在，正在创建: " + bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                System.out.println("[MinIO] bucket创建成功: " + bucketName);
            } else {
                System.out.println("[MinIO] bucket已存在: " + bucketName);
            }
        } catch (Exception e) {
            System.err.println("[MinIO] 初始化失败: " + e.getMessage());
            e.printStackTrace();
            // 如果MinIO初始化失败，抛出异常，防止应用继续运行
            throw new RuntimeException("MinIO初始化失败，请检查MinIO服务和配置", e);
        }
    }

    /**
     * 上传图片至 MinIO
     * 
     * @param file 图片文件
     * @return 所存图片的文件名标识
     */
    /**
     * 从 URL 下载图片并上传至 MinIO
     * 
     * @param imageUrl 图片 URL
     * @return 所存图片的文件名标识
     */
    public String uploadImageFromUrl(String imageUrl) throws Exception {
        log.info("[MinIO] 正在从 URL 下载并上传图片: {}", imageUrl);
        try (InputStream inputStream = java.net.URI.create(imageUrl).toURL().openStream()) {
            // 先尝试读取所有字节以确定大小，或者直接流式上传（但需要 content-length）
            byte[] bytes = inputStream.readAllBytes();
            String objectName = UUID.randomUUID().toString().replaceAll("-", "") + ".jpg";
            
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(bais, bytes.length, -1)
                                .contentType("image/jpeg")
                                .build());
            }
            return objectName;
        }
    }

    public String uploadImage(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String objectName = UUID.randomUUID().toString().replaceAll("-", "") + extension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        }
        return objectName;
    }

    /**
     * 获取带有鉴权的前端可访问直链(7天有效)
     */
    public String getImageUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
    }

    /**
     * 从 URL 或原始对象名中提取 MinIO 对象名
     * @param urlOrName 包含路径或签名的 URL 或者是原始文件名
     * @return 提取出的对象名
     */
    public String extractObjectName(String urlOrName) {
        if (urlOrName == null || urlOrName.trim().isEmpty()) return null;
        
        // 如果是完整的 URL (包含 http/https)
        if (urlOrName.contains("://")) {
            try {
                // 去除可能存在的查询参数 (如 ?X-Amz-Algorithm=...)
                String path = urlOrName.split("\\?")[0];
                // 获取最后一段路径作为对象名
                return path.substring(path.lastIndexOf('/') + 1);
            } catch (Exception e) {
                log.warn("[MinIO] 从接口 URL 提取对象名失败: {}", urlOrName);
                return null;
            }
        }
        
        // 如果包含路径分隔符但不是完整链接
        if (urlOrName.contains("/")) {
            return urlOrName.substring(urlOrName.lastIndexOf('/') + 1);
        }
        
        // 否则认为它本身就是对象名
        return urlOrName;
    }

    /**
     * 删除 MinIO 中的对象
     * @param objectName 对象名称
     */
    public void removeObject(String objectName) {
        try {
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            log.info("[MinIO] 成功删除对象: {}", objectName);
        } catch (Exception e) {
            log.error("[MinIO] 删除对象失败: {}, {}", objectName, e.getMessage());
        }
    }
}
