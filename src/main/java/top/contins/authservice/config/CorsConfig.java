package top.contins.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * 跨域配置
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);

        // 允许的来源
        config.addAllowedOrigin(allowedOrigins);

        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Refresh-Token");
        config.addAllowedHeader("Origin");
        config.addAllowedHeader("X-Requested-With");
        // 明确列出方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setMaxAge(1800L);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}