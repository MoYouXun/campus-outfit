package com.campus.outfit.service;

import com.campus.outfit.dto.AiAnalysisResult;
import com.campus.outfit.entity.WardrobeItem;
import java.util.List;

public interface AiService {
    /**
     * 分析穿搭图片
     * @param imageBytes 图片字节数据
     * @return 分析结果
     */
    AiAnalysisResult analyzeOutfit(byte[] imageBytes);

    /**
     * 为虚拟试衣审计人像底图
     * @param base64Image 人像图 Base64
     * @return 审核结论 JSON 字符串 (包含 isSuitable 和 reason)
     */
    String analyzePortraitForTryOn(String base64Image);

    /**
     * 生成 AI 换装后的图片
     * @param humanImageUrl 人像图Url
     * @param upperGarmentUrl 上衣图Url
     * @param lowerGarmentUrl 裤子图Url
     * @return 生成的图片Base64或链接
     */
    String generateTryOnImage(String personImageUrl, String upperGarmentUrl, String lowerGarmentUrl);

    /**
     * 使用衣柜上下文分析穿搭图片（支持多模态与多轮对话）
     * @param base64Image 用户上传的主图 Base64
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param wardrobeItems 衣柜单品列表
     * @return AI 分析结果 JSON
     */
    String analyzeOutfitWithWardrobe(String base64Image, Long userId, String sessionId, List<WardrobeItem> wardrobeItems);

    /**
     * 在衣柜上下文环境下进行聊天（多轮对话）
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @param wardrobeItems 衣柜单品列表（用于冷启动或增强参考）
     * @return AI 回复
     */
    String chatWithWardrobeContext(String sessionId, String message, List<WardrobeItem> wardrobeItems);

    /**
     * 生成 AI 图像（Seedream）
     * @param prompt 提示词
     * @return 生成出的图片 URL
     */
    String generateImage(String prompt);

    /**
     * 分析并鉴定电子衣柜单品
     * @param base64Image 图片的 Base64 编码
     * @return 大模型返回的 JSON 结果字符串
     */
    String analyzeWardrobeItem(String base64Image);
}
