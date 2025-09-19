package top.contins.authservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.contins.authservice.mapper.UserMapper;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.common.SyncEvent;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.model.vo.LoginResponse;
import top.contins.authservice.service.CaptchaService;
import top.contins.authservice.service.MailService;
import top.contins.authservice.service.RedisEmailTokenService;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;
import top.contins.authservice.util.MailContentUtil;
import top.contins.authservice.util.ObjectConvertUtil;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {


    @Value("${app.name}")
    private String appName;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.official-website:http://localhost}")
    private String baseUrl;

    @Value("${app.organization-name:我们的团队}")
    private String organizationName;

    @Value("${app.contact.email:support@example.com}")
    private String contactEmail;

    @Value("${app.working-hours:9:00-18:00}")
    private String workingHours;


    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final MailContentUtil mailContentUtil;
    private final RedisEmailTokenService redisEmailTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, MailService mailService,
                           MailContentUtil mailContentUtil, RedisEmailTokenService redisEmailTokenService,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil, CaptchaService captchaService,
                           StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.mailService = mailService;
        this.mailContentUtil = mailContentUtil;
        this.redisEmailTokenService = redisEmailTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long getCurrentUserId() {
        // 从Spring Security上下文获取当前登录用户的ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof UserPo user) {
            return user.getUserId();
        }
        return null;
    }

    @Override
    public List<Long> getAll() {
        LambdaQueryWrapper<UserPo> queryWrapper = new LambdaQueryWrapper<>();
        // 筛选出状态为NORMAL的用户
        return userMapper.selectList(queryWrapper).stream()
                .filter(user -> user.getStatus()== UserPo.UserStatus.NORMAL).map(UserPo::getUserId).toList();
    }

    @Override
    public UserPo getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public UserPo getUserByUsername(String username) {
        LambdaQueryWrapper<UserPo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserPo::getUsername, username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public UserPo getUserByEmail(String email) {
        LambdaQueryWrapper<UserPo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserPo::getEmail, email);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public UserPo updateUser(UserPo user) {
        userMapper.updateById(user);
        return user;
    }

    @Override
    public boolean deleteUser(Long userId) {
        // todo : admin only
        int result = userMapper.deleteById(userId);
        return result > 0;
    }

    @Transactional
    @Override
    public boolean deleteUser() {
        Long userId = getCurrentUserId();
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        // 软删除：标记为 DEACTIVATED
        user.setStatus(UserPo.UserStatus.DEACTIVATED);
        userMapper.updateById(user);

        // 发布用户删除事件
        publishUserEvent("delete", userId);

        return true;
    }

    @Override
    public Result<String> registerUser(RegisterRequest request) {
        // 构建用户对象
        UserPo user = ObjectConvertUtil.copyProperties(request, UserPo.class);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        boolean isValid = captchaService.verifyCaptcha(request.captchaId, request.captchaCode);
        if (!isValid) {
            return Result.error("验证码验证失败");
        }
        // 检查用户名和邮箱是否已存在
        UserPo existingUserByUsername = getUserByUsername(user.getUsername());
        if (existingUserByUsername != null) {
            return Result.error("用户名已存在");
        }
        UserPo existingUserByEmail = getUserByEmail(user.getEmail());
        if (existingUserByEmail != null) {
            return Result.error("邮箱已被注册");
        }

        // 判断邮件组件是否配置正确
        if (isMailConfigured()) {
            user.setStatus(UserPo.UserStatus.UNVERIFIED);

            // 发送邮件
            try {
                sendActivationEmail(user);
                log.info("激活邮件已发送至：{}", user.getEmail());
            } catch (Exception e) {
                log.error("发送激活邮件失败：{}", e.getMessage(), e);
                return Result.error("注册失败，邮件发送异常，请稍后再试");
            }

            int rows = userMapper.insert(user);
            if (rows <= 0) {
                throw new RuntimeException("注册失败，请稍后再试");
            }

            return Result.success("注册成功，请查收邮件并激活账户");
        }

        log.warn("邮件组件未启用，跳过邮件验证，用户注册成功，用户ID：{}", user.getUserId());
        user.setStatus(UserPo.UserStatus.NORMAL);

        // 插入新用户
        int rows = userMapper.insert(user);
        if (rows <= 0) {
            throw new RuntimeException("注册失败，请稍后再试");
        }
        return Result.success("注册成功，欢迎加入 " + appName);
    }

    /**
     * 判断邮件组件是否配置正确
     *
     * @return true表示邮件组件已配置，false表示未配置
     */
    private boolean isMailConfigured() {
        return StringUtils.hasText(mailUsername);
    }

    /**
     * 发送账户激活邮件
     *
     * @param user 用户信息
     */
    private void sendActivationEmail(UserPo user) {
        // 构建激活链接
        String activationUrl = mailContentUtil.generateActivationUrl(user.getEmail(), baseUrl + ":" + serverPort);

        // 准备邮件模板参数
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("username", user.getUsername());
        placeholders.put("activationUrl", activationUrl);
        placeholders.put("expirationTime", MailContentUtil.getExpirationTime(null));
        placeholders.put("sendTime", MailContentUtil.getCurrentTime());
        placeholders.put("organization-name", organizationName);
        placeholders.put("app.contact.email", contactEmail);
        placeholders.put("app.working-hours", workingHours);

        // 使用模板发送邮件
        mailService.sendEmailWithTemplate(user.getEmail(), "register", placeholders);
    }

    /**
     * 发送密码重置邮件
     *
     * @param email 用户邮箱
     * @return 操作结果
     */
    @Override
    public Result<String> sendResetPasswordEmail(String email) {
        // 检查邮箱是否存在
        UserPo user = getUserByEmail(email);
        if (user == null) {
            return Result.error("邮箱不存在");
        }

        // 检查邮件组件是否配置
        if (!isMailConfigured()) {
            return Result.error("邮件服务未配置，无法发送重置邮件");
        }

        try {
            sendResetPasswordEmail(user);
            log.info("密码重置邮件已发送至：{}", email);
            return Result.success("密码重置邮件已发送，请查收邮件");
        } catch (Exception e) {
            log.error("发送密码重置邮件失败：{}", e.getMessage(), e);
            return Result.error("邮件发送失败，请稍后再试");
        }
    }

    /**
     * 激活账户
     *
     * @param token 激活令牌
     * @return 操作结果
     */
    @Override
    public Result<String> activateAccount(String token) {
        // 验证并消费token
        String email = redisEmailTokenService.validateAndConsumeToken(token, "activation");

        if (email == null) {
            return Result.error("激活链接无效或已过期");
        }

        // 检查用户是否存在
        UserPo user = getUserByEmail(email);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 检查用户状态
        if (user.getStatus() == UserPo.UserStatus.NORMAL) {
            return Result.error("账户已激活，无需重复激活");
        }

        if (user.getStatus() == UserPo.UserStatus.BANNED || user.getStatus() == UserPo.UserStatus.DEACTIVATED) {
            return Result.error("账户已被锁定，请联系管理员");
        }

        // 激活账户
        user.setStatus(UserPo.UserStatus.NORMAL);
        updateUser(user);

        log.info("用户账户已激活：{}", email);
        publishUserEvent("create", user.getUserId());
        return Result.success("账户激活成功，欢迎使用我们的服务！");
    }

    @Override
    public Result<String> logout(String token) {
        // 对于JWT token，客户端删除即可实现登出
        // 这里可以选择将token加入黑名单，但需要额外的存储机制
        log.info("用户登出");
        return Result.success("登出成功");
    }

    @Override
    public Result<String> validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Result.error("Token不能为空");
        }

        // 如果token以"Bearer "开头，需要去掉前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (jwtUtil.validateToken(token)) {
            // 验证token类型
            String tokenType = jwtUtil.getTokenType(token);
            if ("access".equals(tokenType)) {
                return Result.success("Token有效");
            } else {
                return Result.error("Token类型错误");
            }
        } else {
            return Result.error("Token无效或已过期");
        }
    }

    /**
     * 发送密码重置邮件
     *
     * @param user 用户信息
     */
    private void sendResetPasswordEmail(UserPo user) {
        // 构建密码重置链接
        String resetPasswordUrl = mailContentUtil.generateResetPasswordUrl(user.getEmail(), baseUrl + ":" + serverPort);

        // 准备邮件模板参数
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("username", user.getUsername());
        placeholders.put("resetPasswordUrl", resetPasswordUrl);
        placeholders.put("expirationTime", MailContentUtil.getExpirationTime(null));
        placeholders.put("sendTime", MailContentUtil.getCurrentTime());
        placeholders.put("organization-name", organizationName);
        placeholders.put("app.contact.email", contactEmail);
        placeholders.put("app.working-hours", workingHours);

        // 使用模板发送邮件
        mailService.sendEmailWithTemplate(user.getEmail(), "resetPassword", placeholders);
    }

    @Override
    public Result<?> login(String account, String password) {
        // 根据用户名或邮箱查找用户
        UserPo user = null;
        if (account.contains("@")) {
            user = getUserByEmail(account);
        } else {
            user = getUserByUsername(account);
        }

        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.error("密码错误");
        }

        // 检查用户状态
        if (user.getStatus() == UserPo.UserStatus.UNVERIFIED) {
            return Result.error("账户未激活，请先激活账户");
        }
        if (user.getStatus() == UserPo.UserStatus.BANNED) {
            return Result.error("账户已被封禁，请联系管理员");
        }
        if (user.getStatus() == UserPo.UserStatus.DEACTIVATED) {
            return Result.error("账户已停用，请联系管理员");
        }

        // 从 UserPo 中获取真实的角色名称 (如 "USER" 或 "ADMIN")
        String role = user.getRole().name();
        // 定义受众服务列表，例如用户登录后默认可以访问认证服务和用户资料服务
        List<String> audience = Arrays.asList("auth-service", "user-profile");

        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUsername(), user.getEmail(), role, null, audience);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getUsername(), role, audience);

        LoginResponse response = new LoginResponse(
                accessToken,
                refreshToken);

        log.info("用户登录成功：{}", user.getUsername());
        return Result.success(response);
    }

    @Override
    public Result<?> refreshToken(String refreshToken) {
        // 1. 验证refresh token
        if (!jwtUtil.validateToken(refreshToken)) {
            return Result.error("Refresh token无效或已过期");
        }

        // 2. 检查token类型
        String tokenType = jwtUtil.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            return Result.error("Token类型错误");
        }

        // 3. 获取用户信息
        String username = jwtUtil.getUsernameFromToken(refreshToken);
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);

        // 4. 验证Token信息
        if (username == null || userId == null) {
            return Result.error("Token信息无效");
        }

        // 5. 验证用户是否存在且状态正常
        UserPo user = getUserByUsername(username);
        if (user == null || user.getStatus() != UserPo.UserStatus.NORMAL) {
            return Result.error("用户状态异常，请重新登录");
        }

        // 6. 获取 jti，用于标记“已使用”
        String jti = jwtUtil.getJtiFromToken(refreshToken);
        if (jti == null) {
            return Result.error("Refresh token 无效");
        }

        // 7. 检查是否已被使用（防重放）
        String usedKey = "refresh_token:used:" + jti;
        Boolean isUsed = redisTemplate.hasKey(usedKey);
        if (isUsed) {
            return Result.error("Refresh token 已失效");
        }

        // 8. 标记为已使用（立即废弃旧 refresh token）
        redisTemplate.opsForValue().set(usedKey, "1", Duration.ofDays(7)); // 保留7天，防止重放

        // 9. 从旧的 refreshToken 中提取角色和受众，以保持权限一致
        String role = jwtUtil.getRoleFromToken(refreshToken);
        List<String> audience = jwtUtil.getAudienceFromToken(refreshToken);

        // 10. 生成新的 token
        String newAccessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUsername(), user.getEmail(), role, null, audience);
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getUsername(), role, audience);

        LoginResponse response = new LoginResponse(newAccessToken, newRefreshToken);
        return Result.success(response);
    }
    @Override
    public Result<String> resetPassword(String token, String newPassword, String confirmPassword) {
        // 验证密码确认
        if (!newPassword.equals(confirmPassword)) {
            return Result.error("两次输入的密码不一致");
        }

        // 验证密码强度（可根据需要添加）
        if (newPassword.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }

        // 验证并消费token
        String email = redisEmailTokenService.validateAndConsumeToken(token, "reset-password");
        if (email == null) {
            return Result.error("重置链接无效或已过期");
        }

        // 查找用户
        UserPo user = getUserByEmail(email);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        updateUser(user);

        log.info("用户密码重置成功：{}", email);
        return Result.success("密码重置成功");
    }

    @Override
    public Result<String> changePassword(String oldPassword, String hashedPassword, String confirmPassword) {
        // 验证密码确认
        if (!hashedPassword.equals(confirmPassword)) {
            return Result.error("两次输入的密码不一致");
        }

        // 验证密码强度
        if (hashedPassword.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }

        // 获取当前用户
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }

        UserPo user = getUserById(currentUserId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("旧密码错误");
        }

        // 检查新密码是否与旧密码相同
        if (passwordEncoder.matches(hashedPassword, user.getPassword())) {
            return Result.error("新密码不能与旧密码相同");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(hashedPassword));
        updateUser(user);

        log.info("用户密码修改成功，用户ID：{}", currentUserId);
        return Result.success("密码修改成功");
    }



    public void publishUserEvent(String eventType, Long userId) {
        try {
            // 构造事件对象（与消费者一致）
            SyncEvent event = new SyncEvent(eventType, userId.toString(), getUserById(userId).getStatus().toString());

            // 序列化为 JSON
            String json = objectMapper.writeValueAsString(event);

            // 写入 Redis Stream（
            redisTemplate.opsForStream()
                    .add("sync:auth_to_linx:events", Map.of("event", json)); // key-value 形式存储

            log.info("已发布用户事件: {} - userId={}", eventType, userId);

        } catch (Exception e) {
            log.error("发布用户事件失败: userId={}", userId, e);
        }
    }
}