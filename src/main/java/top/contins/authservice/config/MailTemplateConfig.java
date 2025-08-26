package top.contins.authservice.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Getter
@Configuration
public class MailTemplateConfig {

    private final Map<String, String> templates = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        // 读取注册邮件模板
        loadTemplate("register", "static/email-template/register.html");

        // 读取密码重置邮件模板
        loadTemplate("resetPassword", "static/email-template/reset-password.html");
    }

    /**
     * 加载邮件模板
     * @param templateName 模板名称
     * @param templatePath 模板路径
     * @throws IOException IO异常
     */
    private void loadTemplate(String templateName, String templatePath) throws IOException {
        ClassPathResource templateResource = new ClassPathResource(templatePath);
        if (templateResource.exists()) {
            try (InputStream inputStream = templateResource.getInputStream()) {
                String template = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                templates.put(templateName, template);
            }
        }
    }

}
