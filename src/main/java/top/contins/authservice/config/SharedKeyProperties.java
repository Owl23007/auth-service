package top.contins.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端共享密钥配置
 * <p>
 *TODO : 将服务注册服务迁移到单独的服务中
 */
@Data
@Component
@ConfigurationProperties(prefix = "shared-keys")
public class SharedKeyProperties {
    private Map<String, String> keys = new HashMap<>();
}