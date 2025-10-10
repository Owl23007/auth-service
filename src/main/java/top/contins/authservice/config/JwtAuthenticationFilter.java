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

        String token = getTokenFromRequest(request);

        // 只有当 token 存在且有效时，才进行认证
        if (StringUtils.hasText(token)) {
            try {
                // 先验证 token 是否有效（签名、未过期、类型为 access）
                if (isValidAccessToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    // 确保 userId 有效且当前无认证
                    if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        Collections.emptyList()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("JWT 认证成功：用户 ID: {}", userId);
                    }
                } else {
                    // Token 无效（过期、类型错误等），视为未认证
                    log.warn("收到无效或过期的 Access Token");
                }
            } catch (Exception e) {
                // JWT 解析异常（如格式错误、签名无效等）
                log.error("JWT 解析或验证异常", e);
            }
        }

        // 继续执行后续过滤器（包括 Spring Security 的授权判断）
        filterChain.doFilter(request, response);
    }

    /**
     * 综合校验：是否为有效 Access Token（签名有效 + 未过期 + 类型正确）
     */
    private boolean isValidAccessToken(String token) {
        try {
            // validateToken 应该在内部处理过期、签名等校验
            if (!jwtUtil.validateToken(token)) {
                return false;
            }
            String type = jwtUtil.getTokenType(token);
            return "access".equals(type);
        } catch (Exception e) {
            log.debug("Token 验证异常", e);
            return false;
        }
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}