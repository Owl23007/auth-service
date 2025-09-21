package top.contins.authservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final JwtUtil jwtUtil ;
    private final CaptchaService captchaService ;

    @Autowired
    public AuthController(UserService userService, JwtUtil jwtUtil, CaptchaService captchaService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
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
     * 获取验证码
     *
     * @return 验证码的Base64编码字符串
     */
    @GetMapping("/captcha")
    public Result<String> getCaptcha() {
        String captcha = captchaService.generateCaptcha();  // 返回 id: base64
        return Result.success(captcha);
    }

    /**
     * 用户登出
     * 通过access token继承refresh token的jti进行登出
     * 将jti加入Redis黑名单， 持续时间为token的剩余有效期
     *
     * @param token 用户access token
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result<String> logout(
            @RequestHeader("Authorization") String token,
            @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {

        // 优先使用 refresh token  进行登出
        if (refreshToken != null) {
            Result<String> result = userService.logout(refreshToken);
            // 只要 refresh token 处理成功，直接返回（因为 jti 相同，access token 也会失效）
            if (result.getCode() == 0) {
                return result;
            }
        }
        // 处理 access token
        return userService.logout(token);
    }

    /**
     * 验证token有效性
     *
     * @param token 待验证的token
     * @return 验证结果
     */
    @PostMapping("/validate")
    public Result<String> validateToken(@RequestParam("token") String token) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("Token不能为空");
        }

        // 如果token以"Bearer "开头，需要去掉前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (jwtUtil.validateToken(token)) {
            return Result.success("Token有效");
        } else {
            return Result.error("Token无效或已过期");
        }
    }
}
