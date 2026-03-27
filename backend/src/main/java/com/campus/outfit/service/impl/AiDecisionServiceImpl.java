package com.campus.outfit.service.impl;

import com.campus.outfit.dto.OutfitPkRequest;
import com.campus.outfit.dto.OutfitPkResponse;
import com.campus.outfit.service.AiDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * AI 决策推断服务实现类
 */
@Slf4j
@Service
public class AiDecisionServiceImpl implements AiDecisionService {

    private static final String PK_SYSTEM_PROMPT = "你是一位顶级的私人穿搭顾问。请根据用户提供的搭配A、搭配B及目标场景，进行专业对比分析，并输出胜出者、理由以及五个维度的雷达图评分（正式度、色彩和谐、场景契合、时尚感、保暖度）。";

    @Override
    public OutfitPkResponse pkOutfits(OutfitPkRequest request, Long userId) {
        log.info("[AI PK] 核心提示词：{}", PK_SYSTEM_PROMPT);
        log.info("[AI PK] 正在组装 Prompt 并调用大模型进行 A/B PK...");
        log.info("[AI PK] 场景：{} , 方案A：{} , 方案B：{}", 
                request.getScene(), request.getImageAUrl(), request.getImageBUrl());

        // 创建 Mock 返回值
        OutfitPkResponse response = new OutfitPkResponse();
        response.setWinner("B");
        response.setReason("搭配B的剪裁更利落，颜色组合也更低调高级，极其符合[" + request.getScene() + "]的场景需求，在细节处理上完胜A！");

        // 组装 Mock 雷达图数据
        OutfitPkResponse.RadarData radarData = new OutfitPkResponse.RadarData();
        radarData.setDimensions(Arrays.asList("正式度", "色彩和谐", "场景契合", "时尚感", "保暖度"));
        radarData.setScoresA(Arrays.asList(70, 75, 60, 85, 65));
        radarData.setScoresB(Arrays.asList(85, 90, 95, 80, 70));
        
        response.setRadarData(radarData);

        return response;
    }
}
