package com.campus.outfit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.outfit.entity.Report;

/**
 * 举报信息服务类
 */
public interface ReportService extends IService<Report> {
    /**
     * 提交举报
     */
    void submitReport(Report report, Long reporterId);

    /**
     * 获取待处理举报列表
     */
    IPage<Report> getPendingReports(int page, int size);

    /**
     * 处理举报
     */
    void handleReport(Long reportId, String action);
}
