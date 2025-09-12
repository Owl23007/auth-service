package top.contins.authservice.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.contins.authservice.config.SharedKeyProperties;
import top.contins.authservice.model.common.Result;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import top.contins.authservice.util.JwtUtil;

@Slf4j
@RestController
@RequestMapping("/service-registry")
public class ServiceRegistryController {

    private static final Map<String, ServiceInstance> SERVICE_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Challenge> PENDING_CHALLENGES = new ConcurrentHashMap<>();

    // 注入配置类
    private final SharedKeyProperties sharedKeyProperties;
    private final JwtUtil jwtUtil ;

    public ServiceRegistryController(SharedKeyProperties sharedKeyProperties, JwtUtil jwtUtil) {
        this.sharedKeyProperties = sharedKeyProperties;
        this.jwtUtil = jwtUtil;
    }



    //下游服务发起注册请求
    @PostMapping("/register")
    public Result<?> registerService(@RequestBody RegisterRequest request) {
        cleanupExpiredChallenges();
        log.debug("收到服务注册请求: {}", request);

        if (request.getServiceName() == null || request.getEndpoint() == null || request.getPathPrefix() == null) {
            return Result.error("缺少必要参数: serviceName, endpoint, pathPrefix");
        }

        // 使用注入的配置类
        Map<String, String> serviceSharedKeys = sharedKeyProperties.getKeys();
        if (serviceSharedKeys == null || !serviceSharedKeys.containsKey(request.getServiceName())) {
            return Result.error("未授权的服务: " + request.getServiceName());
        }

        String nonce = generateNonce();
        long timestamp = Instant.now().getEpochSecond();

        Challenge challenge = new Challenge(nonce, timestamp);
        PENDING_CHALLENGES.put(request.getServiceName(), challenge);

        log.info("服务 [{}] 请求注册，已下发 Challenge: nonce={}, timestamp={}",
                request.getServiceName(), nonce, timestamp);

        return Result.success(new ChallengeResponse(
                "/service-registry/challenge-response",
                challenge.getNonce(),
                challenge.getTimestamp()
        ));
    }

    // Step 2: 下游服务提交签名响应
    @PostMapping("/challenge-response")
    public Result<String> challengeResponse(@RequestBody ChallengeResponseRequest request) {
        cleanupExpiredChallenges();

        if (request.getServiceName() == null || request.getSignature() == null) {
            return Result.error("缺少必要参数: serviceName, signature");
        }

        Challenge challenge = PENDING_CHALLENGES.get(request.getServiceName());
        if (challenge == null) {
            return Result.error("无待处理的 Challenge，请先调用 /register");
        }

        if (challenge.isExpired()) {
            PENDING_CHALLENGES.remove(request.getServiceName());
            return Result.error("Challenge 已过期，请重新调用 /register 获取新的 Challenge");
        }

        // 使用注入的配置类
        Map<String, String> serviceSharedKeys = sharedKeyProperties.getKeys();
        String sharedKey = serviceSharedKeys == null ? null : serviceSharedKeys.get(request.getServiceName());
        if (sharedKey == null) {
            PENDING_CHALLENGES.remove(request.getServiceName());
            return Result.error("服务未授权");
        }

        String expectedSignature = calculateHmac(
                sharedKey,
                challenge.getNonce() + "|" + challenge.getTimestamp()
        );

        if (!expectedSignature.equals(request.getSignature())) {
            PENDING_CHALLENGES.remove(request.getServiceName());
            log.warn("服务 [{}] 签名验证失败，预期: {}, 实际: {}", request.getServiceName(), expectedSignature, request.getSignature());
            return Result.error("签名验证失败");
        }

        RegisterRequest originalRequest = request.getOriginalRequest();
        if (originalRequest == null) {
            PENDING_CHALLENGES.remove(request.getServiceName());
            return Result.error("缺少原始注册信息");
        }

        boolean isNew = !SERVICE_REGISTRY.containsKey(request.getServiceName());
        SERVICE_REGISTRY.put(request.getServiceName(), new ServiceInstance(
                request.getServiceName(),
                originalRequest.getEndpoint(),
                originalRequest.getPathPrefix()
        ));

        PENDING_CHALLENGES.remove(request.getServiceName());

        String message = isNew
                ? "服务注册成功"
                : "服务已存在，信息已更新";

        log.info(" {} : {} -> {}", message, request.getServiceName(), originalRequest.getEndpoint());

        String jwt = String.valueOf(jwtUtil.getCurrentPublicKey());

        return Result.success(message,jwt);
    }
    @GetMapping("/routes")
    public Result<Map<String, ServiceInstance>> getRoutes() {
        return Result.success(SERVICE_REGISTRY);
    }

    // 惰性清理过期 Challenge，避免内存泄漏
    private void cleanupExpiredChallenges() {
        long now = Instant.now().getEpochSecond();
        PENDING_CHALLENGES.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) {
                log.debug("清理过期 Challenge: 服务 [{}]", entry.getKey());
            }
            return expired;
        });
    }

    // 生成随机 nonce
    private String generateNonce() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // HMAC-SHA256 签名
    private String calculateHmac(String key, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }

    // ===== DTOs =====

    @Data
    public static class RegisterRequest {
        private String serviceName;
        private String endpoint;
        private String pathPrefix;
    }

    @Data
    @AllArgsConstructor // 生成全参构造函数
    @NoArgsConstructor  // 生成无参构造函数
    public static class ChallengeResponse {
        private String endpoint;
        private String nonce;
        private long timestamp;

    }

    @Data
    public static class ChallengeResponseRequest {
        private String serviceName;
        private String signature;
        private RegisterRequest originalRequest; // 原始注册信息
    }

    @Data
    public static class Challenge {
        private final String nonce;
        private final long timestamp;
        private final long expireAt; // 过期时间戳

        public Challenge(String nonce, long timestamp) {
            this.nonce = nonce;
            this.timestamp = timestamp;
            this.expireAt = timestamp + 30; // 30秒后过期
        }

        public boolean isExpired() {
            return Instant.now().getEpochSecond() > expireAt;
        }
    }

    @Data
    public static class ServiceInstance {
        private String serviceName;
        private String baseUrl;
        private String pathPrefix;

        public ServiceInstance(String serviceName, String baseUrl, String pathPrefix) {
            this.serviceName = serviceName;
            this.baseUrl = baseUrl;
            this.pathPrefix = pathPrefix;
        }
    }
}