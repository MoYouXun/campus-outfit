package com.campus.outfit.aspect;

import com.campus.outfit.entity.AuditLog;
import com.campus.outfit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 管理员操作审计切面
 * 自动记录 AdminController 中的敏感操作（POST/DELETE）到 audit_log 表
 */
@Aspect
@Component
public class AdminAuditAspect {

    @Autowired
    private AuditLogMapper auditLogMapper;

    /**
     * 定义切入点：拦截 AdminController 中所有被 @PostMapping 或 @DeleteMapping 标记的方法
     */
    @Pointcut("execution(* com.campus.outfit.controller.AdminController.*(..)) && " +
            "(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void adminActionPointcut() {}

    /**
     * 在方法成功返回后异步执行审计日志记录
     */
    @AfterReturning(pointcut = "adminActionPointcut()", returning = "result")
    @Async
    public void recordAuditLog(JoinPoint joinPoint, Object result) {
        try {
            // 获取请求上下文，用于提取 IP
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return;
            HttpServletRequest request = attributes.getRequest();
            
            AuditLog auditLog = new AuditLog();
            
            // 1. 获取管理员ID（从 Spring Security 上下文获取）
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long) {
                auditLog.setAdminId((Long) principal);
            } else {
                // 如果是 MyUserDetails 或其他类型，可以适当调整
                // 这里假设之前的 JWT 框架直接存的是 Long id
                auditLog.setAdminId(0L); 
            }
            
            // 2. 获取操作类型（方法名）
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = signature.getMethod().getName();
            auditLog.setActionType(methodName.toUpperCase());
            
            // 3. 获取目标ID（假设 AdminController 里的敏感操作第一个参数都是 Long id）
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0 && args[0] instanceof Long) {
                auditLog.setTargetId((Long) args[0]);
            }
            
            // 4. 其他描述信息
            auditLog.setIpAddress(getIpAddr(request));
            auditLog.setDescription("管理员执行操作: " + methodName + ", 结果: " + (result != null ? "成功" : "未知"));
            auditLog.setCreateTime(LocalDateTime.now());
            
            // 5. 插入数据库
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计日志失败不应阻塞主业务流程
            System.err.println("Audit log recording failed: " + e.getMessage());
        }
    }

    /**
     * 获取真实 IP 的工具方法
     */
    private String getIpAddr(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
