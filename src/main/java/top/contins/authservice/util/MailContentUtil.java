package top.contins.authservice.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.contins.authservice.service.RedisEmailTokenService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class MailContentUtil {

    @Autowired
    private RedisEmailTokenService redisEmailTokenService;

    /**
     * 生成邮箱激活链接
     * 
     * @param email   用户邮箱
     * @param baseUrl 应用基础URL
     * @return 激活链接
     */
    public String generateActivationUrl(String email, String baseUrl) {
        // 生成token并存储到Redis，24小时过期
        String token = redisEmailTokenService.generateAndStoreToken(email, "activation", 24 * 60);

        // 返回激活链接
        return baseUrl + "/registration/activate?token=" + token;
    }

    /**
     * 生成密码重置链接
     * 
     * @param email   用户邮箱
     * @param baseUrl 应用基础URL
     * @return 密码重置链接
     */
    public String generateResetPasswordUrl(String email, String baseUrl) {
        // 生成token并存储到Redis，2小时过期
        String token = redisEmailTokenService.generateAndStoreToken(email, "reset-password", 2 * 60);

        return baseUrl + "/auth/resetPassword?token=" + token;
    }

    /**
     * 格式化邮件内容，将模板中的占位符替换为实际值
     * 
     * @param template     模板内容
     * @param placeholders 占位符映射
     * @return 格式化后的内容
     */
    public static String formatContent(String template, Map<String, Object> placeholders) {
        String result = template;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 生成当前时间字符串
     * 
     * @return 格式化的当前时间
     */
    public static String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 生成过期时间
     *
     * @param expirationTime 过期时间，默认24小时后
     * @return 格式化的过期时间
     */
    public static String getExpirationTime(LocalDateTime expirationTime) {
        if (expirationTime == null) {
            expirationTime = LocalDateTime.now().plusHours(24);
        }
        return expirationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}