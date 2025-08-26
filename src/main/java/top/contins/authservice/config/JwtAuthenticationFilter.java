package top.contins.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT认证过滤器
 * 用于解析JWT token并设置用户认证信息
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Lazy
    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            try {
                // 确保是访问token而不是刷新token
                String tokenType = jwtUtil.getTokenType(token);
                if (!"access".equals(tokenType)) {
                    log.warn("收到非访问token，类型：{}", tokenType);
                    filterChain.doFilter(request, response);
                    return;
                }

                Integer userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);

                if (userId != null && username != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 验证用户是否存在且状态正常
                    UserPo user = userService.getUserById(userId);
                    if (user != null && user.getStatus() == UserPo.UserStatus.NORMAL) {
                        // 创建认证对象
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                user, null, new ArrayList<>());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // 设置认证信息到SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("用户认证成功：{}, userId: {}", username, userId);
                    } else {
                        log.warn("用户不存在或状态异常，用户ID：{}", userId);
                    }
                }
            } catch (Exception e) {
                log.error("JWT认证过程中发生错误", e);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取JWT token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
