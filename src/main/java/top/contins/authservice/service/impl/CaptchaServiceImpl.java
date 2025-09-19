package top.contins.authservice.service.impl;

import com.wf.captcha.GifCaptcha;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.contins.authservice.config.CaptchaConfig;
import top.contins.authservice.service.CaptchaService;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的验证码服务实现
 */
@Service
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate stringRedisTemplate ;
    private final CaptchaConfig captchaConfig ;

    @Autowired
    public CaptchaServiceImpl(StringRedisTemplate stringRedisTemplate, CaptchaConfig captchaConfig) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.captchaConfig = captchaConfig;
    }

    @Override
    public String generateCaptcha() {
        try {
            // 生成验证码图片
            Captcha captcha = new SpecCaptcha(
                    captchaConfig.getWidth(),
                    captchaConfig.getHeight(),
                    captchaConfig.getCharCount());
            captcha.setCharType(Captcha.TYPE_DEFAULT);

            // 生成唯一ID
            String captchaId = UUID.randomUUID().toString();
            String captchaKey = captchaConfig.getRedisKeyPrefix() + captchaId;

            // 存储到Redis，设置过期时间
            stringRedisTemplate.opsForValue().set(
                    captchaKey,
                    captcha.text().toLowerCase(),
                    captchaConfig.getExpireMinutes(),
                    TimeUnit.MINUTES);

            // 将验证码图片转换为Base64字符串
            String base64Image = captcha.toBase64();

            log.debug("生成验证码成功，ID: {}", captchaId);
            return captchaId + ":" + base64Image;

        } catch (Exception e) {
            log.error("生成验证码失败", e);
            throw new RuntimeException("验证码生成失败");
        }
    }

    @Override
    public boolean verifyCaptcha(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }

        try {
            String captchaKey = captchaConfig.getRedisKeyPrefix() + captchaId;
            String storedCode = stringRedisTemplate.opsForValue().get(captchaKey);

            if (storedCode == null) {
                log.debug("验证码不存在或已过期，ID: {}", captchaId);
                return false;
            }

            boolean isMatch = storedCode.equalsIgnoreCase(code.trim());

            if (isMatch) {
                // 验证成功后删除验证码，防止重复使用
                stringRedisTemplate.delete(captchaKey);
                log.debug("验证码验证成功，ID: {}", captchaId);
            } else {
                log.debug("验证码验证失败，ID: {}, 输入: {}, 期望: {}", captchaId, code, storedCode);
            }

            return isMatch;

        } catch (Exception e) {
            log.error("验证码验证过程中发生错误，ID: {}", captchaId, e);
            return false;
        }
    }

    @Override
    public void removeCaptcha(String captchaId) {
        if (captchaId != null) {
            String captchaKey = captchaConfig.getRedisKeyPrefix() + captchaId;
            stringRedisTemplate.delete(captchaKey);
            log.debug("删除验证码，ID: {}", captchaId);
        }
    }
}
