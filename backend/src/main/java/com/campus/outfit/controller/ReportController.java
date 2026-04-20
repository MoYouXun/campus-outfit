package com.campus.outfit.controller;
 
import com.campus.outfit.entity.Report;
import com.campus.outfit.service.ReportService;
import com.campus.outfit.security.JwtUtils;
import com.campus.outfit.utils.Result;
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
 
    @Autowired
    private JwtUtils jwtUtils;
 
    /**
     * 提交举报
     */
    @PostMapping
    public Result<String> submitReport(
            @RequestBody Report report, 
            @RequestHeader("Authorization") String token) {
        
        Long currentUserId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        reportService.submitReport(report, currentUserId);
        return Result.success("举报成功，我们将尽快处理");
    }
}
