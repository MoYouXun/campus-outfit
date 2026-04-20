package com.campus.outfit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.outfit.entity.Outfit;
import com.campus.outfit.entity.User;
import com.campus.outfit.entity.Report;
import com.campus.outfit.entity.SystemDailyStat;
import com.campus.outfit.mapper.SystemDailyStatMapper;
import com.campus.outfit.service.OutfitService;
import com.campus.outfit.service.ReportService;
import com.campus.outfit.service.UserService;
import com.campus.outfit.utils.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private OutfitService outfitService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SystemDailyStatMapper systemDailyStatMapper;

    @GetMapping("/users")
    public Result<IPage<User>> getUsers(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.page(new Page<>(page, size)));
    }

    @DeleteMapping("/user/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/outfits")
    public Result<IPage<Outfit>> getOutfits(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(outfitService.page(new Page<>(page, size)));
    }

    @DeleteMapping("/outfit/{id}")
    public Result<String> deleteOutfit(@PathVariable Long id) {
        outfitService.removeById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/reports")
    public Result<IPage<Report>> getReports(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.success(reportService.getPendingReports(page, size));
    }

    @PostMapping("/reports/{id}/resolve")
    public Result<String> resolveReport(@PathVariable Long id) {
        reportService.handleReport(id, "RESOLVED");
        return Result.success("已处理");
    }

    @PostMapping("/reports/{id}/reject")
    public Result<String> rejectReport(@PathVariable Long id) {
        reportService.handleReport(id, "REJECTED");
        return Result.success("已驳回");
    }

    @GetMapping("/stats/trend")
    public Result<java.util.List<SystemDailyStat>> getStatsTrend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(systemDailyStatMapper.selectList(new LambdaQueryWrapper<SystemDailyStat>()
                .orderByDesc(SystemDailyStat::getStatDate)
                .last("LIMIT " + days)));
    }
}
