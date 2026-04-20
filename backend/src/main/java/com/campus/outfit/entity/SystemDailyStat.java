package com.campus.outfit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 系统每日统计实体类
 */
@Data
@TableName("system_daily_stat")
public class SystemDailyStat implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 新增用户数
     */
    private Integer newUserCount;

    /**
     * 新增穿搭数
     */
    private Integer newOutfitCount;

    /**
     * AI调用次数
     */
    private Integer aiCallCount;

    /**
     * 举报次数
     */
    private Integer reportCount;

    /**
     * 统计执行时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
