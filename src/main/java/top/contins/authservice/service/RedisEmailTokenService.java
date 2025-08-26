package top.contins.authservice.service;

/**
 * Redis邮件token服务接口
 */
public interface RedisEmailTokenService {

    /**
     * 生成并存储邮件验证token
     * 
     * @param email             邮箱
     * @param type              token类型（activation、reset-password等）
     * @param expirationMinutes 过期时间（分钟）
     * @return 生成的token
     */
    String generateAndStoreToken(String email, String type, long expirationMinutes);

    /**
     * 验证并消费token
     * 
     * @param token 待验证的token
     * @param type  token类型
     * @return 关联的邮箱，如果token无效则返回null
     */
    String validateAndConsumeToken(String token, String type);

    /**
     * 删除指定邮箱的所有token
     * 
     * @param email 邮箱
     * @param type  token类型
     */
    void deleteTokensByEmailAndType(String email, String type);

    /**
     * 检查token是否存在
     * 
     * @param token 待检查的token
     * @param type  token类型
     * @return 是否存在
     */
    boolean tokenExists(String token, String type);
}
