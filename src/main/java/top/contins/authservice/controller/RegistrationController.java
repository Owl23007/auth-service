package top.contins.authservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.service.UserService;

/**
 * 用户注册控制器
 * 负责用户注册和账户激活相关功能
 */
@RestController
@RequestMapping("/auth/registration")
@Validated
public class RegistrationController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * 
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterRequest request) {
        return userService.registerUser(request);
    }

    /**
     * 激活账户
     * 
     * @param token 激活令牌
     * @return 激活结果
     */
    @GetMapping("/activate")
    public Result<String> activateAccount(@RequestParam("token") String token) {
        return userService.activateAccount(token);
    }
}
