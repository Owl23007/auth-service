package top.contins.authservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求类
 */
@Data
public class UserLoginRequest implements Serializable {
    /**
     * 用户名或邮箱
     */
    @NotBlank(message = "用户名或邮箱不能为空")
    private String account;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    private String captchaId;
    private String captchaCode;

    /**
     * 客户端类型（必填）
     * 用于区分设备/平台，未来可用于策略控制、限流、个性化 Token 策略
     */
    // @NotBlank(message = "客户端类型不能为空") 未来支持
    private String clientType;

    /**
     * 客户端ID（必填）如：前端注册的 clientId（OAuth2 风格）
     * 用于识别具体应用，用于权限隔离、审计、限流
     */
    // @NotBlank(message = "客户端ID不能为空")
    private String clientId;

    /**
     * 设备唯一标识
     * 用于“记住设备”、“踢人”、“多设备管理”
     */
    private String deviceId;
}
