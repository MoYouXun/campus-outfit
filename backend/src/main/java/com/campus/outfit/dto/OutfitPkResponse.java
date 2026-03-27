package com.campus.outfit.dto;

import lombok.Data;
import java.util.List;

/**
 * 穿搭 PK 响应对象
 */
@Data
public class OutfitPkResponse {
    private String winner; // 获胜者 (A 或 B)
    private String reason; // 获胜理由
    private RadarData radarData;

    /**
     * 雷达图数据
     */
    @Data
    public static class RadarData {
        private List<String> dimensions; // 维度名称 (如：清凉度, 时尚感)
        private List<Integer> scoresA; // A 的得分
        private List<Integer> scoresB; // B 的得分
    }
}
