package top.contins.authservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.service.MailService;
import top.contins.authservice.service.UserService;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户注册控制器
 * 负责用户注册和账户激活相关功能
 */
@RestController
@RequestMapping("/registration")
@Validated
public class RegistrationController {

    private final UserService userService;
    private final MailService mailService;

    @Autowired
    public RegistrationController(UserService userService, MailService mailService) {
        this.userService = userService;
        this.mailService = mailService;
    }

    @Value("${app.official-website}")
    private String officialWebsite;

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
    public String activateAccount(@RequestParam("token") String token) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("website", officialWebsite);
        if(userService.activateAccount(token).getCode().equals(0)){
            // 激活成功,显示成功页面

            return mailService.getHTMLContent("welcome", placeholders);
        }
        return mailService.getHTMLContent("error", placeholders);
    }
}
