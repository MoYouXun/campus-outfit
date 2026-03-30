package com.campus.outfit.service;

import com.campus.outfit.dto.AiChatRequest;
import com.campus.outfit.vo.AiOutfitRecommendVO;

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
     * 通用聊天
     * @param message 消息
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @return 响应文本
     */
    String chat(String message, String sessionId, Long userId);

    /**
     * 强力推荐对话：返回结构化的穿搭推荐 VO
     * @param request 对话请求 DTO
     * @param userId 用户 ID
     * @return 穿搭推荐 VO
     */
    AiOutfitRecommendVO chatForOutfit(AiChatRequest request, Long userId);
}
