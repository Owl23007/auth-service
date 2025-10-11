package top.contins.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * 验证码配置类
 */
@Configuration
@ConfigurationProperties(prefix = "app.captcha")
@Data
public class CaptchaConfig {

    /**
     * 验证码宽度
     */
    private int width = 100;

    /**
     * 验证码高度
     */
    private int height = 40;

    /**
     * 验证码字符数
     */
    private int charCount = 4;

    /**
     * 验证码过期时间（分钟）
     */
    private int expireMinutes = 5;

    /**
     * Redis key前缀
     */
    private String redisKeyPrefix = "captcha:";
}
