package com.campus.outfit.controller;

import com.campus.outfit.entity.Report;
import com.campus.outfit.service.ReportService;
import com.campus.outfit.utils.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 举报功能接口
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 提交举报
     */
    @PostMapping
    public Result<String> submitReport(@RequestBody Report report, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        reportService.submitReport(report, currentUserId);
        return Result.success("举报成功，我们将尽快处理");
    }
}
