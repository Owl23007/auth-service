package top.contins.authservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import top.contins.authservice.service.RedisEmailTokenService;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis邮件token服务实现类
 */
@Service
@Slf4j
public class RedisEmailTokenServiceImpl implements RedisEmailTokenService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Redis key前缀
    private static final String TOKEN_PREFIX = "email_token:";
    private static final String EMAIL_TO_TOKEN_PREFIX = "email_to_token:";

    @Override
    public String generateAndStoreToken(String email, String type, long expirationMinutes) {
        // 生成唯一token
        String token = UUID.randomUUID().toString().replace("-", "");

        // Redis key设计
        String tokenKey = TOKEN_PREFIX + type + ":" + token;
        String emailToTokenKey = EMAIL_TO_TOKEN_PREFIX + type + ":" + email;

        try {
            // 先删除该邮箱之前的token
            deleteTokensByEmailAndType(email, type);

            // 存储token -> email映射
            redisTemplate.opsForValue().set(tokenKey, email, expirationMinutes, TimeUnit.MINUTES);

            // 存储email -> token映射，用于快速查找和删除
            redisTemplate.opsForValue().set(emailToTokenKey, token, expirationMinutes, TimeUnit.MINUTES);

            log.info("为邮箱 {} 生成并存储 {} 类型token，过期时间：{} 分钟", email, type, expirationMinutes);
            return token;

        } catch (Exception e) {
            log.error("为邮箱 {} 生成 {} 类型token失败", email, type, e);
            throw new RuntimeException("Token生成失败", e);
        }
    }

    @Override
    public String validateAndConsumeToken(String token, String type) {
        String tokenKey = TOKEN_PREFIX + type + ":" + token;

        try {
            // 获取token关联的邮箱
            Object emailObj = redisTemplate.opsForValue().get(tokenKey);
            if (emailObj == null) {
                log.warn("Token未找到或已过期：{}", token);
                return null;
            }

            String email = emailObj.toString();

            // 消费token（删除）
            redisTemplate.delete(tokenKey);

            // 同时删除email到token的映射
            String emailToTokenKey = EMAIL_TO_TOKEN_PREFIX + type + ":" + email;
            redisTemplate.delete(emailToTokenKey);

            log.info("邮箱 {} 的 {} 类型token验证并消费成功", email, type);
            return email;

        } catch (Exception e) {
            log.error("验证并消费token失败，token：{}，类型：{}", token, type, e);
            return null;
        }
    }

    @Override
    public void deleteTokensByEmailAndType(String email, String type) {
        try {
            String emailToTokenKey = EMAIL_TO_TOKEN_PREFIX + type + ":" + email;

            // 获取该邮箱的token
            Object tokenObj = redisTemplate.opsForValue().get(emailToTokenKey);
            if (tokenObj != null) {
                String token = tokenObj.toString();
                String tokenKey = TOKEN_PREFIX + type + ":" + token;

                // 删除token和映射
                redisTemplate.delete(tokenKey);
                redisTemplate.delete(emailToTokenKey);

                log.info("删除邮箱 {} 的现有 {} 类型token", email, type);
            }

        } catch (Exception e) {
            log.error("删除邮箱 {} 的 {} 类型token失败", email, type, e);
        }
    }

    @Override
    public boolean tokenExists(String token, String type) {
        String tokenKey = TOKEN_PREFIX + type + ":" + token;
        try {
            return redisTemplate.hasKey(tokenKey);
        } catch (Exception e) {
            log.error("检查token是否存在失败，token：{}，类型：{}", token, type, e);
            return false;
        }
    }

    /**
     * 清理过期的token
     */
    public void cleanupExpiredTokens() {
        try {
            // Redis会自动清理过期的key，但我们也可以手动清理
            Set<String> tokenKeys = redisTemplate.keys(TOKEN_PREFIX + "*");
            if (!tokenKeys.isEmpty()) {
                log.info("在Redis中发现 {} 个token键", tokenKeys.size());
            }
        } catch (Exception e) {
            log.error("清理过期token失败", e);
        }
    }
}
