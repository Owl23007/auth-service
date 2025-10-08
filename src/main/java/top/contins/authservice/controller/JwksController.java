package top.contins.authservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.contins.authservice.util.JwtUtil;

import java.util.List;
import java.util.Map;

/**
 * JWKS 端点控制器
 * 提供 /jwks.json 接口，返回当前 JWT 签名公钥的标准 JWKS 格式
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
@Slf4j
public class JwksController {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getJwks() {
        try {
            List<Map<String, Object>> jwks = jwtUtil.getJwks();
            ObjectNode root = objectMapper.createObjectNode();
            root.set("keys", objectMapper.valueToTree(jwks));
            return root;
        } catch (Exception e) {
            log.error("生成 JWKS 失败", e);
            // 返回空 keys 数组
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.set("keys", objectMapper.createArrayNode());
            return fallback;
        }
    }
}