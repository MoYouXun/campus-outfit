package com.campus.outfit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.outfit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员审计日志 Mapper 接口
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
