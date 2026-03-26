package com.campus.outfit.service;

import com.campus.outfit.entity.Outfit;
import java.util.List;

public interface RankingService {
    /**
     * 获取热度排行榜
     */
    List<Outfit> getHotRanking(int limit);

    /**
     * 获取风格排行榜
     */
    List<Outfit> getStyleRanking(String style, int limit);

    /**
     * 获取校园排行榜
     */
    List<Outfit> getSchoolRanking(String school, int limit);

    /**
     * 定时任务刷新排行榜（模拟，由定时任务调用）
     */
    void refreshRankings();
}
