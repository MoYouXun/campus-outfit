package com.campus.outfit.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
     * 从 MinIO 删除图片
     * 
     * @param objectName 图片文件名标识
     */
    public void deleteImage(String objectName) throws Exception {
        minioClient.removeObject(
                io.minio.RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());
    }

    /**
     * 获取图片并转为 Base64 格式
     */
    public String getImageAsBase64(String objectName) throws Exception {
        try (InputStream in = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        )) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }
}
