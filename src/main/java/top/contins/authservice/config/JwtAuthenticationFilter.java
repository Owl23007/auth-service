package top.contins.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import top.contins.authservice.util.JwtUtil;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 获取 JWT
        String token = getTokenFromRequest(request);

        // 验证 JWT
        if (StringUtils.hasText(token)) {
            try {
                if (!isValidAccessToken(token)) {
                    log.warn("无效或过期的 Access Token");

                    // 设置响应状态为 401 Unauthorized
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");

                    // 可选：返回 JSON 错误信息
                    String errorJson = "{\"code\": 401, \"message\": \"Invalid or expired access token\"}";
                    response.getWriter().write(errorJson);

                    return; // 终止 Filter 链
                }

                // 1. 从 JWT 中提取用户信息
                Long userId = jwtUtil.getUserIdFromToken(token);

                // 2. 创建 Authentication 对象并存储在 SecurityContext 中
                if (userId != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 创建 Authentication 对象, 将用户 ID 存储在认证对象中
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    Collections.emptyList()
                            );

                    // 设置请求详情
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 将认证对象存储在 SecurityContext 中
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("JWT 认证成功：用户 ID: {}", userId);
                }
            } catch (Exception e) {
                log.error("JWT 认证异常", e);
                SecurityContextHolder.clearContext();
            }
        }

        // 继续处理请求
        filterChain.doFilter(request, response);
    }

    /**
     * 综合校验：是否为有效 Access Token（签名 + 未过期 + 类型正确）
     */
    private boolean isValidAccessToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return false;
        }
        String type = jwtUtil.getTokenType(token);
        return "access".equals(type);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}