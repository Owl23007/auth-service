package top.contins.authservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.UserLoginRequest;
import top.contins.authservice.service.CaptchaService;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;

/**
 * 认证控制器
 * 负责用户登录、token刷新、验证码等认证相关功能
 */
@RestController
@RequestMapping("/auth")
@Validated
@Slf4j
public class AuthController {
    private final UserService userService ;
    private final CaptchaService captchaService ;

    @Autowired
    public AuthController(UserService userService,CaptchaService captchaService) {
        this.userService = userService;
        this.captchaService = captchaService;
    }

    /**
     * 用户登录
     *
     * @param loginRequest 登录请求
     * @return 登录结果，包含access token和refresh token
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody @Validated UserLoginRequest loginRequest) {
        return userService.login(loginRequest);
    }

    /**
     * 刷新access token
     *
     * @param refreshToken 刷新token
     * @return 新的access token
     */
    @PostMapping("/refresh")
    public Result<?> refreshToken(@RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Result.error("Refresh token 不能为空");
        }

        // 兼容使用 Bearer 前缀
        if (refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7).trim();
        }

        return userService.refreshToken(refreshToken);
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/userinfo")
    public Result<?> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error("未认证");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            Long userId = (Long) principal;
            return Result.success(userService.getUserById(userId));
        }

        return Result.error("无法获取用户信息");
    }

    /**
     * 获取验证码
     *
     * @return 验证码的Base64编码字符串
     */
    @GetMapping("/captcha")
    public Result<String> getCaptcha() {
        log.info("test");
        String captcha = captchaService.generateCaptcha();  // 返回 id: base64
        return Result.success(captcha);
    }

    /**
     * 用户登出
     * 通过access token继承refresh token的jti进行登出
     * 将jti加入Redis黑名单， 持续时间为token的剩余有效期
     *
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌（可选）
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result<String> logout(
            @RequestHeader("Authorization") String accessToken,
            @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        // 统一提取 token
        String pureAccessToken = extractToken(accessToken);
        String pureRefreshToken = refreshToken != null ? extractToken(refreshToken) : null;

        // 优先使用 refresh token
        if (pureRefreshToken != null) {
            Result<String> result = userService.logout(pureRefreshToken);
            if (result.getCode() == 0){
                return result;
            }
        }

        return userService.logout(pureAccessToken);
    }

    private String extractToken(String token) {
        if (token == null) return null;
        return token.trim().startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }
}
