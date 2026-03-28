package com.campus.outfit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        // 排除无需过滤的公开接口路径
        return uri.startsWith("/api/user/login") ||
               uri.startsWith("/api/user/register") ||
               uri.startsWith("/api/community/") ||
               uri.startsWith("/api/topic/") ||
               uri.startsWith("/api/outfit/test-analyze") ||
               uri.startsWith("/api/outfit/analyze") ||
               uri.startsWith("/api/outfit/upload") ||
               uri.startsWith("/api/weather/") ||
               uri.startsWith("/api/recommend/season") ||
               uri.startsWith("/api/recommend/occasion") ||
               uri.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateToken(jwt)) {
                Long userId = jwtUtils.getUserIdFromToken(jwt);
                String role = jwtUtils.getRoleFromToken(jwt);

                // 根据解析到的角色构建权限，在 Spring Security 中规范使用 "ROLE_" 前缀
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                // 采用无状态验证，直接将从 token 获得的关联标识注册进当前上下文
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.singletonList(authority));

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Token 验证失败或异常则无需设置认证，让 Security 自动在后续链路拦截
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
