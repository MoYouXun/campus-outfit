package com.campus.outfit.service;

public interface RankingService {
    /**
     * 获取热度排行榜
     */
    java.util.List<com.campus.outfit.entity.Outfit> getHotRanking(String gender, int limit);



    /**
     * 定时任务刷新排行榜（模拟，由定时任务调用）
     */
    void refreshRankings();
}
