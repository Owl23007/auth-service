package top.contins.authservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.ResetPasswordRequest;
import top.contins.authservice.service.RedisEmailTokenService;
import top.contins.authservice.service.UserService;

/**
 * 密码管理控制器
 * 负责密码重置和密码修改相关功能
 */
@RestController
@RequestMapping("/user/password")
@Validated
public class PasswordController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisEmailTokenService redisEmailTokenService;

    /**
     * 发送密码重置邮件
     * 
     * @param email 用户邮箱
     * @return 发送结果
     */
    @PostMapping("/send-reset")
    public Result<String> sendResetPasswordEmail(@RequestParam("email") String email) {
        return userService.sendResetPasswordEmail(email);
    }

    /**
     * 验证密码重置token有效性
     * 
     * @param token 重置密码令牌
     * @return 验证结果
     */
    @GetMapping("/validate")
    public Result<String> validateResetPasswordToken(@RequestParam("token") String token) {
        if (redisEmailTokenService.tokenExists(token, "reset-password")) {
            return Result.success("Token有效");
        } else {
            return Result.error("Token无效或已过期");
        }
    }

    /**
     * 重置密码
     * 
     * @param request 重置密码请求
     * @return 重置结果
     */
    @PostMapping("/reset")
    public Result<String> resetPassword(@RequestBody @Validated ResetPasswordRequest request) {
        return userService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
    }
}
