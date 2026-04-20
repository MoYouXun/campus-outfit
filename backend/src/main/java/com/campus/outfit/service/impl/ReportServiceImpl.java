package com.campus.outfit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.outfit.entity.Report;
import com.campus.outfit.exception.BusinessException;
import com.campus.outfit.mapper.ReportMapper;
import com.campus.outfit.service.ReportService;
import org.springframework.stereotype.Service;

/**
 * 举报信息服务实现类
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    @Override
    public void submitReport(Report report, Long reporterId) {
        // 1. 防重校验
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, report.getTargetType())
                .eq(Report::getTargetId, report.getTargetId())
                .eq(Report::getIsDeleted, 0));
        
        if (count > 0) {
            throw new BusinessException("您已举报过该内容，请耐心等待审核");
        }

        // 2. 初始化数据
        report.setReporterId(reporterId);
        report.setStatus("PENDING");
        
        // 3. 保存
        this.save(report);
    }

    @Override
    public IPage<Report> getPendingReports(int page, int size) {
        return this.page(new Page<>(page, size), new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, "PENDING")
                .orderByDesc(Report::getCreateTime));
    }

    @Override
    public void handleReport(Long reportId, String status) {
        Report report = new Report();
        report.setId(reportId);
        report.setStatus(status);
        this.updateById(report);
    }
}
