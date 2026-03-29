package com.campus.outfit.service;

/**
 * AI 穿搭助手服务接口
 */
public interface AiAssistantService {

    /**
     * 分析穿搭主图并给出建议
     * @param base64Image 主图 Base64
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @return 增强后的 JSON 结果
     */
    String analyze(String base64Image, String sessionId, Long userId);

    /**
     * 基于上下文进行穿搭对话，并可能触发生图
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @param userId 用户 ID
     * @return 增强后的 JSON 结果
     */
    String chat(String sessionId, String message, Long userId);
}
