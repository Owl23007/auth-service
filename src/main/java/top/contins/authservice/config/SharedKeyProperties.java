package top.contins.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "shared-keys")
public class SharedKeyProperties {
    private Map<String, String> keys = new HashMap<>();
}