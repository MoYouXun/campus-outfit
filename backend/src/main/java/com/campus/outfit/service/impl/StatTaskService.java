package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.outfit.entity.*;
import com.campus.outfit.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 系统数据统计定时任务集
 */
@Service
public class StatTaskService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OutfitMapper outfitMapper;

    @Autowired
    private AiAnalysisRecordMapper aiAnalysisRecordMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private SystemDailyStatMapper systemDailyStatMapper;

    /**
     * 每日凌晨 2 点执行昨天的统计
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void calculateDailyStats() {
        // 统计昨天的日期
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = LocalDateTime.of(yesterday, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(yesterday, LocalTime.MAX);

        // 1. 统计新增用户
        Long newUserCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, startOfDay)
                .lt(User::getCreateTime, endOfDay));

        // 2. 统计新增穿搭
        Long newOutfitCount = outfitMapper.selectCount(new LambdaQueryWrapper<Outfit>()
                .ge(Outfit::getCreateTime, startOfDay)
                .lt(Outfit::getCreateTime, endOfDay));

        // 3. 统计 AI 调用次数
        Long aiCallCount = aiAnalysisRecordMapper.selectCount(new LambdaQueryWrapper<AiAnalysisRecord>()
                .ge(AiAnalysisRecord::getCreateTime, startOfDay)
                .lt(AiAnalysisRecord::getCreateTime, endOfDay));

        // 4. 统计举报次数
        Long reportCount = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .ge(Report::getCreateTime, startOfDay)
                .lt(Report::getCreateTime, endOfDay));

        // 5. 写入汇总表
        SystemDailyStat stat = new SystemDailyStat();
        stat.setStatDate(yesterday);
        stat.setNewUserCount(newUserCount.intValue());
        stat.setNewOutfitCount(newOutfitCount.intValue());
        stat.setAiCallCount(aiCallCount.intValue());
        stat.setReportCount(reportCount.intValue());

        // 检查是否已存在（重跑任务的情况）
        SystemDailyStat existing = systemDailyStatMapper.selectOne(new LambdaQueryWrapper<SystemDailyStat>()
                .eq(SystemDailyStat::getStatDate, yesterday));
        
        if (existing != null) {
            stat.setId(existing.getId());
            systemDailyStatMapper.updateById(stat);
        } else {
            systemDailyStatMapper.insert(stat);
        }
    }
}
