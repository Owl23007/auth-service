package top.contins.authservice.service;

/**
 * 验证码服务接口
 */
public interface CaptchaService {

    /**
     * 生成验证码
     * 
     * @return 验证码ID:Base64图片
     */
    String generateCaptcha();

    /**
     * 验证验证码
     * 
     * @param captchaId 验证码ID
     * @param code      用户输入的验证码
     * @return 是否验证成功
     */
    boolean verifyCaptcha(String captchaId, String code);

    /**
     * 删除验证码
     * 
     * @param captchaId 验证码ID
     */
    void removeCaptcha(String captchaId);
}
