package top.contins.authservice.util;

import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.ThreadLocalRandom;
import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.contins.authservice.model.po.UserPo;
import top.contins.authservice.model.common.TokenResponse;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JWT 工具类
 * <p>
 * <h2>核心特性</h2>
 * <ul>
 *   <li><b>双 Token 机制</b>：Access Token（短期） + Refresh Token（长期）</li>
 *   <li><b>动态密钥轮换</b>：自动轮换 RSA 密钥，支持平滑过渡（首次启动即双密钥）</li>
 *   <li><b>JWKS 端点</b>：暴露 /.well-known/jwks.json 供网关验证</li>
 *   <li><b>服务隔离</b>：通过 audience 字段限制 Token 使用范围</li>
 *   <li><b>标准合规</b>：严格遵循 RFC 7519，保留 sub 字段（= userId 字符串）</li>
 * </ul>
 * <p>
 * <h2>JWT 字段说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>来源</th><th>说明</th></tr>
 *   <tr><td>iss</td><td>String</td><td>标准声明</td><td>签发者（默认: auth）</td></tr>
 *   <tr><td>sub</td><td>String</td><td>标准声明</td><td><b>用户唯一标识 = userId.toString()</b></td></tr>
 *   <tr><td>aud</td><td>List<String></td><td>标准声明</td><td>受众服务列表（如 ["linx", "audit"]）</td></tr>
 *   <tr><td>exp</td><td>Date</td><td>标准声明</td><td>过期时间</td></tr>
 *   <tr><td>iat</td><td>Date</td><td>标准声明</td><td>签发时间</td></tr>
 *   <tr><td>jti</td><td>String</td><td>自定义声明</td><td>JWT 唯一ID（用于登出）</td></tr>
 *   <tr><td>userId</td><td>Long</td><td>自定义声明</td><td>用户ID（业务代码友好，避免字符串转换）</td></tr>
 *   <tr><td>role</td><td>String</td><td>自定义声明</td><td>用户角色</td></tr>
 *   <tr><td>scope</td><td>List<String></td><td>自定义声明</td><td>权限域（如 ["read", "write"]）</td></tr>
 *   <tr><td>type</td><td>String</td><td>自定义声明</td><td>Token 类型（"access" 或 "refresh"）</td></tr>
 * </table>
 * <p>
 * <h2>安全设计</h2>
 * <ul>
 *   <li><b>密钥保留策略</b>：密钥保留时间 = max(accessTokenTTL, refreshTokenTTL) + 轮换间隔
 *       （确保所有有效 Token 都能被验证）</li>
 *   <li><b>kid 标识</b>：每个密钥有唯一 kid，JWT Header 中携带 kid 供验证端选择公钥</li>
 *   <li><b>无敏感信息</b>：JWT 中不包含密码、邮箱等敏感数据</li>
 *   <li><b>平滑轮换</b>：首次启动即生成 current + next 双密钥，避免网关验证失败窗口</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtUtil {

    /**
     * Access Token 有效期（毫秒）
     * 默认: 1小时 (3600000 ms)
     */
    @Value("${jwt.expire.access-token:3600000}")
    private Long accessTokenExpiration;

    @Value("${jwt.key.size:2048}")
    private int keySize;

    /**
     * Refresh Token 有效期（毫秒）
     * 默认: 7天 (604800000 ms)
     */
    @Getter
    @Value("${jwt.expire.refresh-token:604800000}")
    private Long refreshTokenExpiration;

    /**
     * JWT 签发者 (iss)
     * 默认: "auth"
     */
    @Value("${jwt.issuer:auth}")
    private String issuer;

    /**
     * 是否启用密钥自动轮换
     * 默认: false
     */
    @Value("${jwt.key.rotation.enabled:false}")
    private boolean keyRotationEnabled;

    /**
     * 密钥轮换间隔（小时）
     * 默认: 48 小时
     */
    @Value("${jwt.key.rotation.interval-hours:48}")
    private int keyRotationIntervalHours;

    /**
     * 所有活跃密钥缓存：kid -> JwtKeyPair
     * 线程安全，供 JWKS 端点和 Token 验证使用
     */
    private final Map<String, JwtKeyPair> activeKeys = new ConcurrentHashMap<>();

    /**
     * 当前用于签名的密钥（最新已启用的密钥）
     */
    private volatile JwtKeyPair currentSigningKey;

    /**
     * 预备密钥（下一轮将提升为当前签名密钥）
     */
    private volatile JwtKeyPair nextSigningKey;

    /**
     * 密钥保留时间（毫秒）
     * = max(accessTokenTTL, refreshTokenTTL) + 轮换间隔
     * 确保所有有效 Token 都能被验证
     */
    private long keyRetentionMs;




    /**
     * 初始化 JWT 工具类
     * <ul>
     *   <li>计算密钥保留时间</li>
     *   <li>生成初始 current 和 next 双密钥（确保首次启动有冗余）</li>
     *   <li>启动密钥轮换任务（如果启用）</li>
     * </ul>
     */
    @PostConstruct
    public void init() {
        long maxTokenTtl = Math.max(accessTokenExpiration, refreshTokenExpiration);
        long rotationIntervalMs = TimeUnit.HOURS.toMillis(keyRotationIntervalHours);
        this.keyRetentionMs = maxTokenTtl + rotationIntervalMs;

        // 首次启动：立即生成 current 和 next 两个密钥，避免验证窗口缺失
        JwtKeyPair initialCurrent = generateKeyPair();
        JwtKeyPair initialNext = generateKeyPair();

        this.currentSigningKey = initialCurrent;
        this.nextSigningKey = initialNext;

        activeKeys.put(initialCurrent.getKid(), initialCurrent);
        activeKeys.put(initialNext.getKid(), initialNext);

        log.info("JWT 初始化完成，已生成双密钥:");
        log.info("  - 当前签名密钥 (current): {}", initialCurrent.getKid());
        log.info("  - 预备密钥 (next): {}", initialNext.getKid());

        if (keyRotationEnabled) {
            log.info("JWT 密钥轮换已启用，每 {} 小时轮换一次，密钥最长保留 {} 小时",
                    keyRotationIntervalHours, TimeUnit.MILLISECONDS.toHours(keyRetentionMs));
        }
    }

    /**
     * 轮换密钥 + 清理过期密钥（原子操作）
     * <p>
     * - 将预备密钥 (nextSigningKey) 提升为当前签名密钥
     * - 生成新的预备密钥
     * - 清理已过保留期的密钥
     */
    @Scheduled(fixedDelayString = "${jwt.key.rotation.interval-hours:48}h",initialDelayString = "${jwt.key.rotation.interval-hours:48}h")
    public synchronized void rotateKeyAndCleanup() {
        if (!keyRotationEnabled) {
            return; // 防止配置热更新后意外执行
        }

        if (nextSigningKey == null) {
            log.warn("预备密钥为空，跳过本次轮换");
            return;
        }

        // 提升预备密钥为当前签名密钥
        this.currentSigningKey = this.nextSigningKey;
        log.info("密钥已切换，当前签名密钥 Key ID: {}", currentSigningKey.getKid());

        // 生成新的预备密钥
        JwtKeyPair newNext = generateKeyPair();
        this.nextSigningKey = newNext;
        activeKeys.put(newNext.getKid(), newNext);
        log.info("新预备密钥已生成，Key ID: {}", newNext.getKid());

        // 清理过期密钥
        cleanupExpiredKeys();
        log.info("已完成密钥轮换和过期密钥清理，当前活跃密钥数: {}", activeKeys.size());
    }

    /**
     * 生成一个新的 RSA 密钥对
     *
     * @return 新的 JwtKeyPair 实例
     */
    private JwtKeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(keySize);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            String kid = generateKid();
            return new JwtKeyPair(kid, keyPair.getPrivate(), keyPair.getPublic());
        } catch (NoSuchAlgorithmException e) {
            log.error("生成 RSA 密钥失败", e);
            throw new RuntimeException("无法生成 RSA 密钥对", e);
        }
    }

    /**
     * 清理过期密钥
     * <p>
     * 保留条件：key.createdAt + keyRetentionMs > 当前时间
     */
    private void cleanupExpiredKeys() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, JwtKeyPair> entry : activeKeys.entrySet()) {
            JwtKeyPair key = entry.getValue();
            if (key.getCreatedAt() + keyRetentionMs < now) {
                toRemove.add(entry.getKey());
            }
        }

        for (String kid : toRemove) {
            activeKeys.remove(kid);
            log.info("清理过期密钥: {}", kid);
        }
    }

    // ===== Token 生成 =====

    /**
     * 生成 Access Token 和 Refresh Token
     *
     * @param user      用户实体（必须包含 userId）
     * @param role      用户角色
     * @param scope     权限域列表（可为 null）
     * @param audience  受众服务列表（如 ["linx", "audit"]）
     * @return 包含 accessToken 和 refreshToken 的响应对象
     */
    public TokenResponse generateToken(UserPo user, String role, List<String> scope, List<String> audience) {
        String jti = generateJti();
        String accessToken = generateAccessToken(user.getUserId(), role, scope, audience, jti);
        String refreshToken = generateRefreshToken(user.getUserId(), role, scope, audience, jti);
        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * 生成 Access Token
     */
    private String generateAccessToken(Long userId, String role,
                                       List<String> scope, List<String> audience, String jti) {
        Map<String, Object> claims = buildBaseClaims(userId, role, scope, jti);
        claims.put("type", "access");
        return createToken(claims, userId, accessTokenExpiration, audience);
    }

    /**
     * 生成 Refresh Token
     */
    private String generateRefreshToken(Long userId, String role,
                                        List<String> scope, List<String> audience, String jti) {
        Map<String, Object> claims = buildBaseClaims(userId, role, scope, jti);
        claims.put("type", "refresh");
        return createToken(claims, userId, refreshTokenExpiration, audience);
    }

    /**
     * 构建基础 Claims（不含 type 字段）
     */
    private Map<String, Object> buildBaseClaims(Long userId, String role, List<String> scope, String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString()); // 业务友好：Long 类型
        claims.put("role", role);
        claims.put("scope", scope != null ? scope : Collections.emptyList());
        claims.put("jti", jti);
        return claims;
    }

    /**
     * 创建 JWT Token
     *
     * @param claims     自定义声明
     * @param userId     用户ID（用于设置 sub 字段）
     * @param expiration 有效期（毫秒）
     * @param audience   受众列表
     * @return 签名后的 JWT 字符串
     */
    private String createToken(Map<String, Object> claims, Long userId, Long expiration, List<String> audience) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        String shortUserId = Base62Util.encode(userId);

        JwtBuilder builder = Jwts.builder()
                .claims(claims)
                .subject(shortUserId) // 使用Base62编码用户ID
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(currentSigningKey.getPrivateKey())
                .header().add("kid", currentSigningKey.getKid()).and();

        if (audience != null && !audience.isEmpty()) {
            ClaimsMutator.AudienceCollection<?> ac = builder.audience();
            for (String aud : audience) {
                if (aud != null && !aud.trim().isEmpty()) {
                    ac.add(aud.trim());
                }
            }
        }

        return builder.compact();
    }

    /**
     * 生成有序、唯一、可读的 Key ID (kid)
     * 格式: {timestamp}_{nano6}_{random8}
     * 示例: 1717023600123_456789_a1b2c3d4
     *  - timestamp: 毫秒时间戳（13位），保证大体顺序
     *  - nano6: 纳秒低6位（000000~999999），防止同一毫秒冲突
     *  - random8: 10位 Base62 随机字符串（0-9A-Za-z），进一步防冲突
     * 总长度 31 字符
     *
     * @return 生成的 Key ID
     */
    private String generateKid() {
        long unixSecond = System.currentTimeMillis();
        long nanoPart = System.nanoTime() % 1_000_000; // 取纳秒的低6位

        // 生成10位 Base62 随机字符串
        String randomPart = generateRandomBase62(10);

        return String.format("%d_%06d_%s", unixSecond, nanoPart, randomPart);
    }

    /**
     * 生成指定长度的 Base62 随机字符串（字符集: 0-9A-Za-z）
     */
    private String generateRandomBase62(int length) {
        final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    // ===== Token 验证 =====

    /**
     * 验证 Token 有效性（签名 + 过期）
     *
     * @param token JWT 字符串
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证 Refresh Token（类型 + 有效性）
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token) && "refresh".equals(getTokenType(token));
    }

    /**
     * 从 Token 中解析 Claims（带 kid 验证）
     *
     * @param token JWT 字符串
     * @return Claims 对象
     * @throws IllegalArgumentException 如果 kid 无效或签名失败
     */
    private Claims getClaimsFromToken(String token) {
        // 1. 手动解析 Header 获取 kid
        String kid = extractKidFromToken(token);
        if (kid == null) {
            throw new IllegalArgumentException("Token 缺少 kid");
        }

        // 2. 查找对应公钥
        JwtKeyPair keyPair = activeKeys.get(kid);
        if (keyPair == null) {
            throw new IllegalArgumentException("未知的 kid: " + kid + "，可能密钥已轮换");
        }

        // 3. 验证签名并解析 Claims
        try {
            return Jwts.parser()
                    .verifyWith(keyPair.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new IllegalArgumentException("Token 验证失败（签名无效或已过期）", e);
        }
    }

    /**
     * 手动从 JWT 中提取 kid（不依赖 JJWT 解析）
     */
    private String extractKidFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT 格式非法：必须是三段式");
        }

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);

            // 使用正则提取
            Pattern pattern = Pattern.compile("\"kid\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(headerJson);
            if (!matcher.find()) {
                return null;
            }

            String kid = matcher.group(1);

            // 校验 kid 是否符合预期格式(只允许字母数字与下划线)
            if (!kid.matches("^[a-zA-Z0-9_-]+$")) {
                log.warn("非法 kid 格式: {}", kid);
                return null; // 或抛异常
            }

            return kid;

        } catch (Exception e) {
            log.warn("解析 JWT Header 失败", e);
            return null;
        }
    }
    // ===== Claims 提取方法 =====

    /**
     * 从 Token 中获取用户ID
     * <p>
     * 优先从 sub 字段 Base62 解析（标准做法），兜底使用 userId 字段
     */

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);

            // 1. 优先从标准字段 `sub` 解析
            String sub = claims.getSubject();
            if (sub != null && !sub.isEmpty()) {
                try {
                    return Base62Util.decode(sub); // 解码 Base62
                } catch (Exception e) {
                    log.warn("无法解码 sub 字段为 Base62: '{}'", sub);
                }
            }

            // 2. 兜底：尝试从自定义字段 `userId` 获取
            Long userId = claims.get("userId", Long.class);
            if (userId != null) {
                return userId;
            }

            log.warn("无法识别用户, sub 与 userId 均无效");
            return null;

        } catch (Exception e) {
            log.error("获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 从 Token 中获取角色
     */
    public String getRoleFromToken(String token) {
        try {
            return getClaimsFromToken(token).get("role", String.class);
        } catch (Exception e) {
            log.error("获取角色失败", e);
            return null;
        }
    }

    /**
     * 从 Token 中获取权限域（scope）
     */
    public List<String> getScopesFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Object scope = claims.get("scope");
            if (scope instanceof List) {
                return ((List<?>) scope).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            } else if (scope instanceof String s) {
                if (s.trim().isEmpty()) return Collections.emptyList();
                return Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("获取 scope 失败", e);
        }
        return Collections.emptyList();
    }

    /**
     * 从 Token 中获取受众服务列表
     */
    public List<String> getAudienceFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Object aud = claims.get("aud");
            if (aud instanceof List) {
                return ((List<?>) aud).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            } else if (aud instanceof String s) {
                if (s.trim().isEmpty()) return Collections.emptyList();
                return List.of(s.trim());
            }
        } catch (Exception e) {
            log.error("获取 audience 失败", e);
        }
        return Collections.emptyList();
    }

    /**
     * 获取 Token 类型（"access" 或 "refresh"）
     */
    public String getTokenType(String token) {
        try {
            return getClaimsFromToken(token).get("type", String.class);
        } catch (Exception e) {
            log.error("获取 token 类型失败", e);
            return null;
        }
    }

    /**
     * 获取 JWT 唯一ID（jti）
     */
    public String getJtiFromToken(String token) {
        try {
            return getClaimsFromToken(token).get("jti", String.class);
        } catch (Exception e) {
            log.error("获取 jti 失败", e);
            return null;
        }
    }

    /**
     * 获取 Token 剩余有效时间（秒）
     */
    public long getTokenRemainingTime(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 检查 Token 是否包含指定服务权限
     */
    public boolean hasServiceAccess(String token, String serviceName) {
        try {
            List<String> audiences = getAudienceFromToken(token);
            return audiences != null && audiences.contains(serviceName);
        } catch (Exception e) {
            log.warn("检查服务权限失败", e);
            return false;
        }
    }

    /**
     * 检查 Token 是否具有指定角色
     */
    public boolean hasRole(String token, String role) {
        try {
            String tokenRole = getRoleFromToken(token);
            return role != null && role.equalsIgnoreCase(tokenRole);
        } catch (Exception e) {
            log.warn("检查角色失败", e);
            return false;
        }
    }

    // ===== JWKS 支持 =====

    /**
     * 生成 JWKS（JSON Web Key Set）
     * <p>
     * 供网关动态获取公钥验证 JWT
     *
     * @return JWK 列表
     */
    public List<Map<String, Object>> getJwks() {
        return activeKeys.values().stream().map(keyPair -> {
            try {
                Map<String, Object> jwk = new HashMap<>();
                jwk.put("kty", "RSA");
                jwk.put("kid", keyPair.getKid());
                jwk.put("use", "sig");
                jwk.put("alg", "RS256");
                jwk.put("n", keyPair.getN()); // 直接使用缓存值
                jwk.put("e", keyPair.getE());
                return jwk;
            } catch (Exception ex) {
                log.error("生成 JWK 失败 for kid: {}", keyPair.getKid(), ex);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
    // ===== 工具方法 =====

    /**
     * 生成 JWT 唯一ID（jti）
     */
    public String generateJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ===== 内部类 =====

    /**
     * JWT 密钥对封装
     */
    @Getter
    public static class JwtKeyPair {
        private final String kid;
        private final PrivateKey privateKey;
        private final PublicKey publicKey;
        private final long createdAt;

        // 缓存
        private final String n;
        private final String e;

        public JwtKeyPair(String kid, PrivateKey privateKey, PublicKey publicKey) {
            this.kid = kid;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.createdAt = System.currentTimeMillis();

            // 预计算 n 和 e
            RSAPublicKey rsaPub = (RSAPublicKey) publicKey;
            this.n = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(stripLeadingZeros(rsaPub.getModulus().toByteArray()));
            this.e = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(stripLeadingZeros(rsaPub.getPublicExponent().toByteArray()));
        }

        // 移除大数编码中的前导零（JWK 标准要求）
        private static byte[] stripLeadingZeros(byte[] bytes) {
            int i = 0;
            while (i < bytes.length && bytes[i] == 0) i++;
            return Arrays.copyOfRange(bytes, i, bytes.length);
        }
    }
}