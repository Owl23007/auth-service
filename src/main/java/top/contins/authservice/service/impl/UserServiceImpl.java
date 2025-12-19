package top.contins.authservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.contins.authservice.mapper.UserMapper;
import top.contins.authservice.model.common.Result;
import top.contins.authservice.model.dto.RegisterRequest;
import top.contins.authservice.model.dto.UpdateProfileRequest;
import top.contins.authservice.model.dto.UserLoginRequest;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.model.vo.UserPublicProfileVO;
import top.contins.authservice.model.vo.UserSelfProfileVO;
import top.contins.authservice.service.CaptchaService;
import top.contins.authservice.service.MailService;
import top.contins.authservice.service.MailRedisTokenService;
import top.contins.authservice.service.UserService;
import top.contins.authservice.util.JwtUtil;
import top.contins.authservice.util.MailContentUtil;
import top.contins.authservice.util.ObjectConvertUtil;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.codec.digest.DigestUtils.sha256;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {


    @Value("${app.name:auth}")
    private String appName;

    @Value("${spring.mail.username:}")
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

    private static final String BLACKLIST_TOKEN_KEY_PREFIX = "auth:blacklist:token_jti:";

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final MailContentUtil mailContentUtil;
    private final MailRedisTokenService mailRedisTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    @Autowired
    public UserServiceImpl(UserMapper userMapper, MailService mailService,
                           MailContentUtil mailContentUtil, MailRedisTokenService mailRedisTokenService,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil, CaptchaService captchaService,
                           StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.mailService = mailService;
        this.mailContentUtil = mailContentUtil;
        this.mailRedisTokenService = mailRedisTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Page<UserPo> getUserList(int pageSize, int pageNum, String status, String keyword, String sortBy, String sortOrder) {
        // 创建分页对象
        Page<UserPo> page = new Page<>(pageNum, pageSize);

        // 创建查询构造器
        LambdaQueryWrapper<UserPo> queryWrapper = new LambdaQueryWrapper<>();

        // 1. 状态过滤（如果不是 "ALL"）
        if (!"ALL".equals(status)) {
            queryWrapper.eq(UserPo::getStatus, status);
        }

        // 2. 关键字模糊搜索（用户名或邮箱）
        if (keyword != null && !keyword.trim().isEmpty()) {
            String likeKeyword = "%" + keyword.trim() + "%";
            queryWrapper.and(wrapper -> wrapper
                    .like(UserPo::getUsername, likeKeyword)
                    .or()
                    .like(UserPo::getEmail, likeKeyword)
            );
        }

        // 3. 排序处理（白名单校验防止 SQL 注入）
        if (sortBy != null && sortOrder != null)  {
            boolean isAsc = "asc".equalsIgnoreCase(sortOrder); //  默认为降序

            switch (sortBy) {
                case "username" -> queryWrapper.orderBy(isAsc, true, UserPo::getUsername);
                case "email" -> queryWrapper.orderBy(isAsc, true, UserPo::getEmail);
                case "status" -> queryWrapper.orderBy(isAsc, true, UserPo::getStatus);
                case "created_at" -> queryWrapper.orderBy(isAsc, true, UserPo::getCreateAt);
                case "updated_at" -> queryWrapper.orderBy(isAsc, true, UserPo::getUpdateAt);
                default -> queryWrapper.orderByDesc(UserPo::getCreateAt); // 默认排序
            }
        } else {
            // 默认排序
            queryWrapper.orderByDesc(UserPo::getCreateAt);
        }

        // 4. 返回当前页数据
        return userMapper.selectPage(page, queryWrapper);
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
    public int updateUser(UserPo user) {
       return userMapper.updateById(user);
    }

    @Override
    public int updateUserStatus(UserPo.UserStatus status, Long userId) {
        return 0;
    }

    @Override
    public int deleteUser(Long userId) {
        return userMapper.deleteById(userId);
    }

    @Transactional
    @Override
    public boolean softDeleteUser(Long userId) {
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        // 软删除：标记为 DEACTIVATED
        user.setStatus(UserPo.UserStatus.DEACTIVATED);
        userMapper.updateById(user);

        return true;
    }

    @Override
    public Result<String> register(RegisterRequest request) {
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
        String email = mailRedisTokenService.validateAndConsumeToken(token, "activation");

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
        if(updateUser(user) <= 0) {
            return Result.error("账户激活失败，请稍后再试");
        }
        return Result.success("账户激活成功，欢迎使用我们的服务！");
    }

    @Override
    public Result<String> logout(String token) {
        // 1. 基本验证
        if (!jwtUtil.validateToken(token)) {
            return Result.error("Token无效或已过期");
        }

        //  2. 获取token中的jti，用于构建Redis Key
        String jti = jwtUtil.getJtiFromToken(token);

        if (jti == null) {
            return Result.error("Token无效或已过期");
        }

        // 3. 默认过期时间为refresh token的过期时间
        long expiration = jwtUtil.getRefreshTokenExpiration();

        // 4. 如果是refresh token，则使用当前refresh token的过期时间
        if (jwtUtil.getTokenType(token).equals("refresh")) {
             expiration = jwtUtil.getTokenRemainingTime(token);
        }

        if (expiration > 0) {
            String blacklistKey = BLACKLIST_TOKEN_KEY_PREFIX + jti;
            redisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofMillis(expiration));
        }
        return Result.success("登出成功");
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
    public Result<?> login(UserLoginRequest request) {
        String account = request.getAccount();
        String password = request.getPassword();

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
        // 定义受众服务列表，用户登录后默认可以访问认证服务和用户资料服务
        List<String> audience = Arrays.asList("auth", "linx","synapse","audit");

        List<String> scopes = List.of("linx");

        return Result.success(jwtUtil.generateToken(user, role,scopes, audience));
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
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);

        // 4. 验证Token信息
        if (userId == null) {
            return Result.error("Token信息无效");
        }

        // 5. 验证用户是否存在且状态正常
        UserPo user = getUserById(userId);
        if (user == null || user.getStatus() != UserPo.UserStatus.NORMAL) {
            return Result.error("用户状态异常，请重新登录");
        }

        // 6. 获取 jti，用于标记“已使用”
        String jti = jwtUtil.getJtiFromToken(refreshToken);
        if (jti == null) {
            return Result.error("Refresh token 无效");
        }

        // 7. 检查是否已被使用（防重放）
        String usedKey = BLACKLIST_TOKEN_KEY_PREFIX + jti;
        Boolean isUsed = redisTemplate.hasKey(usedKey);
        if (isUsed) {
            return Result.error("Refresh token 已失效");
        }

        // 8. 标记为已使用（立即废弃旧 refresh token）
        redisTemplate.opsForValue().set(usedKey, "1", Duration.ofDays(7)); // 保留7天，防止重放

        // 9. 从旧的 refreshToken 中提取角色和受众，以保持权限一致
        String role = jwtUtil.getRoleFromToken(refreshToken);
        List<String> scopes = jwtUtil.getScopesFromToken(refreshToken);
        List<String> audience = jwtUtil.getAudienceFromToken(refreshToken);


        return Result.success(jwtUtil.generateToken(user, role,scopes, audience));
    }
    @Override
    public Result<String> resetPassword(String token, String newPassword) {
        // 验证密码强度（可根据需要添加）
        if (newPassword.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }

        // 验证并消费token
        String email = mailRedisTokenService.validateAndConsumeToken(token, "reset-password");
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
        if (updateUser(user) <= 0) {
            return Result.error("密码重置失败，请稍后再试");
        }

        log.info("用户密码重置成功：{}", email);
        return Result.success("密码重置成功");
    }

    @Override
    public Result<String> updatePassword(Long userId, String oldPassword, String newPassword) {
        // 验证旧密码
        UserPo user = getUserById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        if (updateUser(user) <= 0) {
            return Result.error("密码更新失败，请稍后再试");
        }
        log.info("用户密码更新成功：{}", userId);
        return Result.success("密码更新成功");
    }

    @Override
    public Result<?> getSelfProfile(Long userId) {
        try {
            UserPo user = getUserById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 转换为自己的完整资料VO
            UserSelfProfileVO profileVO = new UserSelfProfileVO();

            profileVO.setUserId(user.getUserId());
            profileVO.setUsername(user.getUsername());
            profileVO.setNickname(user.getNickname());
            profileVO.setEmail(user.getEmail());
            profileVO.setPhone(user.getPhone());
            profileVO.setSignature(user.getSignature());
            profileVO.setAvatarImage(user.getAvatarImage());
            profileVO.setBackgroundImage(user.getBackgroundImage());
            profileVO.setStatus(user.getStatus().name());
            profileVO.setRole(user.getRole().name());
            profileVO.setUpdateTime(user.getUpdateAt());


            return Result.success(profileVO);
        } catch (Exception e) {
            log.error("获取用户个人资料失败,用户ID:{}", userId, e);
            return Result.error("获取个人资料失败");
        }
    }

    @Override
    public Result<?> getPublicProfile(Long userId) {
        try {
            UserPo user = getUserById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 检查用户状态
            if (user.getStatus() != UserPo.UserStatus.NORMAL) {
                return Result.error("用户状态异常");
            }

            // 转换为公开资料VO
            UserPublicProfileVO profileVO = new UserPublicProfileVO();
            profileVO.setUserId(user.getUserId());
            profileVO.setUsername(user.getUsername());
            profileVO.setNickname(user.getNickname());
            profileVO.setSignature(user.getSignature());
            profileVO.setAvatarImage(user.getAvatarImage());
            profileVO.setBackgroundImage(user.getBackgroundImage());

            return Result.success(profileVO);
        } catch (Exception e) {
            log.error("获取用户公开资料失败,用户ID:{}", userId, e);
            return Result.error("获取用户资料失败");
        }
    }

    @Override
    @Transactional
    public Result<?> updateProfile(Long userId, Object request) {
        try {
            UserPo user = getUserById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 检查用户状态
            if (user.getStatus() != UserPo.UserStatus.NORMAL) {
                return Result.error("用户状态异常,无法更新资料");
            }

            // 转换请求对象
            UpdateProfileRequest updateRequest;
            if (request instanceof UpdateProfileRequest) {
                updateRequest = (UpdateProfileRequest) request;
            } else {
                return Result.error("无效的请求参数");
            }

            // 更新用户信息
            boolean updated = false;
            if (updateRequest.getNickname() != null) {
                user.setNickname(updateRequest.getNickname());
                updated = true;
            }
            if (updateRequest.getSignature() != null) {
                user.setSignature(updateRequest.getSignature());
                updated = true;
            }
            if (updateRequest.getAvatarImage() != null) {
                user.setAvatarImage(updateRequest.getAvatarImage());
                updated = true;
            }
            if (updateRequest.getBackgroundImage() != null) {
                user.setBackgroundImage(updateRequest.getBackgroundImage());
                updated = true;
            }
            if (updateRequest.getPhone() != null) {
                user.setPhone(updateRequest.getPhone());
                updated = true;
            }

            if (!updated) {
                return Result.error("没有需要更新的内容");
            }

            // 执行更新
            int result = updateUser(user);
            if (result > 0) {
                log.info("用户资料更新成功,用户ID:{}", userId);
                // 返回更新后的完整资料
                return getSelfProfile(userId);
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新用户资料失败,用户ID:{}", userId, e);
            return Result.error("更新资料失败: " + e.getMessage());
        }
    }

    @Override
    public Result<?> getPublicProfileByUsername(String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }

            UserPo user = getUserByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 检查用户状态
            if (user.getStatus() != UserPo.UserStatus.NORMAL) {
                return Result.error("用户状态异常");
            }

            // 转换为公开资料VO
            UserPublicProfileVO profileVO = new UserPublicProfileVO();
            profileVO.setUserId(user.getUserId());
            profileVO.setUsername(user.getUsername());
            profileVO.setNickname(user.getNickname());
            profileVO.setSignature(user.getSignature());
            profileVO.setAvatarImage(user.getAvatarImage());
            profileVO.setBackgroundImage(user.getBackgroundImage());

            return Result.success(profileVO);
        } catch (Exception e) {
            log.error("根据用户名获取用户公开资料失败,用户名:{}", username, e);
            return Result.error("获取用户资料失败");
        }
    }

    @Override
    public Result<?> searchUsers(String keyword, int pageNum, int pageSize) {
        try {
            // 参数校验
            if (pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }

            // 调用已有的getUserList方法,只返回NORMAL状态的用户
            Page<UserPo> userPage = getUserList(pageSize, pageNum, "NORMAL", keyword, "username", "asc");

            // 转换为公开资料VO列表
            Page<UserPublicProfileVO> resultPage = new Page<>(pageNum, pageSize, userPage.getTotal());
            List<UserPublicProfileVO> profileList = userPage.getRecords().stream()
                    .map(user -> {
                        UserPublicProfileVO profileVO = new UserPublicProfileVO();
                        profileVO.setUserId(user.getUserId());
                        profileVO.setUsername(user.getUsername());
                        profileVO.setNickname(user.getNickname());
                        profileVO.setSignature(user.getSignature());
                        profileVO.setAvatarImage(user.getAvatarImage());
                        profileVO.setBackgroundImage(user.getBackgroundImage());
                        return profileVO;
                    })
                    .collect(Collectors.toList());
            
            resultPage.setRecords(profileList);

            return Result.success(resultPage);
        } catch (Exception e) {
            log.error("搜索用户失败,关键字:{}", keyword, e);
            return Result.error("搜索用户失败");
        }
    }
}