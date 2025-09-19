package top.contins.authservice.util;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT 工具类（支持双Token + 角色 + 服务隔离 + 精确登出 + RSA动态生成 + Key ID）
 * <p>
 * Claims 设计：
 * - userId: 用户ID
 * - sub: 用户名
 * - email: 邮箱
 * - role: 角色（USER/ADMIN）
 * - scope: 权限域（如 ["linx", "ugc"]）
 * - aud: 受众服务（如 ["linx:create", "ai-agent"]）
 * - jti: JWT唯一ID（用于登出黑名单）
 * - type: token类型（access/refresh）
 * <p>
 * Header 扩展：
 * - kid: 密钥唯一标识（Key ID），用于未来多密钥轮换
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.expiration:7200000}") // 2小时（毫秒）
    private Long expiration;

    @Value("${app.jwt.refresh-expiration:604800000}") // 7天（毫秒）
    private Long refreshExpiration;

    @Value("${app.jwt.issuer:auth-service}")
    private String issuer;

    // 动态生成的密钥对
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String keyId;

    /**
     * 启动时动态生成 RSA 密钥对（2048位）和唯一 Key ID
     */
    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();

            // 生成唯一 Key ID —— 可选：UUID / 指纹（如SHA-256公钥摘要）
            this.keyId = UUID.randomUUID().toString().replace("-", "");

            log.info("RSA密钥对已动态生成，算法：RS256，Key ID: {}", keyId);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("无法生成RSA密钥对", e);
        }
    }

    /**
     * 生成访问Token
     */
    public String generateAccessToken(Long userId, String username, String email,
                                      String role, List<String> scope, List<String> audience) {
        return generateAccessToken(userId, username, email, role, scope, audience, generateJti());
    }

    /**
     * 生成访问Token（带指定jti）
     */
    public String generateAccessToken(Long userId, String username, String email,
                                      String role, List<String> scope, List<String> audience, String jti) {
        Map<String, Object> claims = buildBaseClaims(userId, username, email, role, scope, jti);
        claims.put("type", "access");
        return createToken(claims, username, expiration, audience);
    }

    /**
     * 生成刷新Token
     */
    public String generateRefreshToken(Long userId, String username, String role, List<String> audience) {
        return generateRefreshToken(userId, username, role, audience, generateJti());
    }

    /**
     * 生成刷新Token（带指定jti）
     */
    public String generateRefreshToken(Long userId, String username, String role,
                                       List<String> audience, String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("type", "refresh");
        claims.put("jti", jti);
        return createToken(claims, username, refreshExpiration, audience);
    }

    /**
     * 构建基础Claims
     */
    private Map<String, Object> buildBaseClaims(Long userId, String username, String email,
                                                String role, List<String> scope, String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("scope", scope != null ? scope : Collections.emptyList());
        claims.put("jti", jti);
        return claims;
    }

    /**
     * 创建Token —— 使用 RSA 私钥签名 + 嵌入 kid 到 Header
     */
    private String createToken(Map<String, Object> claims, String subject, Long expiration, List<String> audience) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey)
                .header().add("kid", keyId).and();

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
     * 生成JWT唯一ID（jti）
     */
    public String generateJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 验证Token（签名 + 过期）—— 使用公钥验证
     */
    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token); // 自动校验签名和过期
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 验证刷新Token（签名 + 过期 + 类型）
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token) && "refresh".equals(getTokenType(token));
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("从token获取用户名失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("从token获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取邮箱
     */
    public String getEmailFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("email", String.class);
        } catch (Exception e) {
            log.error("从token获取邮箱失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("从token获取角色失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取权限域（scope)
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
                if (s.trim().isEmpty()) {
                    return Collections.emptyList();
                }
                return Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("从token获取scope失败", e);
        }
        return Collections.emptyList();
    }

    /**
     * 从Token中获取受众服务（audience）
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
                if (s.trim().isEmpty()) {
                    return Collections.emptyList();
                }
                return List.of(s.trim());
            }
        } catch (Exception e) {
            log.error("从token获取audience失败", e);
        }
        return Collections.emptyList();
    }

    /**
     * 获取Token类型
     */
    public String getTokenType(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("type", String.class);
        } catch (Exception e) {
            log.error("从token获取类型失败", e);
            return null;
        }
    }

    /**
     * 获取JWT唯一ID（jti）
     */
    public String getJtiFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("jti", String.class);
        } catch (Exception e) {
            log.error("从token获取jti失败", e);
            return null;
        }
    }

    /**
     * 从Token中获取Claims（统一入口，带异常包装）—— 使用公钥验证
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new IllegalArgumentException("无效或过期的 JWT Token", e);
        }
    }

    /**
     * 获取Token剩余有效时间（秒）
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
     * 检查Token是否包含指定服务权限
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
     * 检查Token是否具有指定角色
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

    // 👇 可选：提供 kid 获取方法，便于未来暴露 JWKS 端点
    public String getCurrentKeyId() {
        return this.keyId;
    }

    public PublicKey getCurrentPublicKey() {
        return this.publicKey;
    }

    /**
     * 获取当前公钥的 PEM 格式字符串（用于传输或暴露给其他服务）
     * 输出示例：
     * -----BEGIN PUBLIC KEY-----
     * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw2dJjXZ5...
     * -----END PUBLIC KEY-----
     */
    public String getPublicKeyAsPem() {
        if (this.publicKey == null) {
            throw new IllegalStateException("公钥尚未设置");
        }

        // 获取公钥的编码字节数组（X.509 格式）
        byte[] encoded = this.publicKey.getEncoded();

        // Base64 编码
        String base64 = Base64.getEncoder().encodeToString(encoded);

        // 按每行 64 字符分段（符合 PEM 标准）
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");
        int length = base64.length();
        for (int i = 0; i < length; i += 64) {
            int endIndex = Math.min(i + 64, length);
            pem.append(base64, i, endIndex).append("\n");
        }
        pem.append("-----END PUBLIC KEY-----\n");

        return pem.toString();
    }
}