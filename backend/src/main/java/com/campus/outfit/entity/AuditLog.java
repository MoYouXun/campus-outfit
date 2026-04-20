package com.campus.outfit.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员审计日志实体类
 */
@Data
@TableName("audit_log")
public class AuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 执行者ID
     */
    private Long adminId;

    /**
     * 操作类型 (DELETE_USER/DELETE_OUTFIT/RESOLVE_REPORT/REJECT_REPORT等)
     */
    private String actionType;

    /**
     * 操作目标ID
     */
    private Long targetId;

    /**
     * 详细描述
     */
    private String description;

    /**
     * 操作人IP
     */
    private String ipAddress;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
