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
        return userService.login(loginRequest.getAccount(), loginRequest.getPassword());
    }

    /**
     * 刷新access token
     *
     * @param refreshToken 刷新token
     * @return 新的access token
     */
    @PostMapping("/refresh")
    public Result<?> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return userService.refreshToken(refreshToken);
    }

    /**
     * 获取验证码
     *
     * @return 验证码的Base64编码字符串
     */
    @GetMapping("/captcha")
    public Result<String> getCaptcha() {
        String captcha = captchaService.generateCaptcha();
        return Result.success(captcha);
    }

    /**
     * 用户登出
     *
     * @param token 用户token
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        // 对于JWT无状态token，服务端不需要维护状态
        // 客户端删除本地存储的token即可实现登出
        log.info("用户登出");
        return Result.success("登出成功");
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
