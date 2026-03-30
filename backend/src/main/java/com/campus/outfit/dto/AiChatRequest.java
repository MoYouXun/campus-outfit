package com.campus.outfit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 穿搭对话请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户输入的对话消息内容
     */
    private String message;

    /**
     * 会话唯一标识 ID
     * 用于在服务端追踪多轮对话的上下文状态
     */
    private String sessionId;

    /**
     * 用户对话中引用的图片 URL 列表
     * 可包含多张穿搭参考图
     */
    private List<String> imageUrls;
}
