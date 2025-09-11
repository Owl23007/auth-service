package top.contins.authservice.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class MailTemplateConfig {

    private static final String TEMPLATE_LOCATION_PATTERN = "classpath*:static/email-template/*.html";

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Set<String> registeredTemplateNames = ConcurrentHashMap.newKeySet(); // 记录合法模板名

    /**
     * 获取模板内容（按需加载 + 缓存）
     */
    public String getTemplateContent(String templateName) {
        if (!registeredTemplateNames.contains(templateName)) {
            log.warn("请求的模板未注册: {}", templateName);
            // 可选：抛异常或返回 null
        }
        return templateCache.computeIfAbsent(templateName, name -> {
            try {
                return loadTemplateFromFile(name);
            } catch (Exception e) {
                log.error("加载邮件模板失败: {}", name, e);
                throw new RuntimeException("加载邮件模板失败: " + name, e);
            }
        });
    }

    @PostConstruct
    public void init() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(TEMPLATE_LOCATION_PATTERN);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".html")) {
                    String kebabName = filename.substring(0, filename.length() - 5);
                    String camelName = kebabToCamel(kebabName);
                    if (camelName == null || camelName.trim().isEmpty()) {
                        log.warn("模板文件名转换后为空，跳过: {}", filename);
                        continue;
                    }
                    // 只注册模板名，不预加载内容
                    registeredTemplateNames.add(camelName);
                    log.debug("自动注册模板: {} (文件: {})", camelName, filename);
                }
            }

            if (registeredTemplateNames.isEmpty()) {
                log.warn("未找到任何邮件模板文件，路径模式: {}", TEMPLATE_LOCATION_PATTERN);
            } else {
                log.info("已自动注册 {} 个邮件模板: {}", registeredTemplateNames.size(), registeredTemplateNames);
            }
        } catch (IOException e) {
            log.error("扫描邮件模板失败", e);
            throw new RuntimeException("初始化邮件模板配置失败", e);
        }
    }

    private String loadTemplateFromFile(String templateName) throws IOException {
        String kebabFileName = camelToKebab(templateName);
        String pattern = TEMPLATE_LOCATION_PATTERN.replace("*.html", kebabFileName + ".html");
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(pattern);

        if (resources.length == 0) {
            throw new IOException("模板文件不存在: " + kebabFileName + ".html");
        }
        if (resources.length > 1) {
            log.warn("找到多个同名模板文件: {}", kebabFileName);
        }

        Resource resource = resources[0];
        try (InputStream inputStream = resource.getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            log.debug("模板首次加载: {}", templateName);
            return content;
        }
    }

    public void clearCache() {
        templateCache.clear();
        log.info("邮件模板缓存已清空");
    }

    public void reloadTemplate(String templateName) {
        templateCache.remove(templateName);
        log.info("模板已标记为重新加载: {}", templateName);
    }

    public void reloadAllTemplates() {
        templateCache.clear();
        // 可选：重新扫描注册（但 registeredTemplateNames 通常不变）
        log.info("所有模板缓存已清空，下次访问将重新加载");
    }

    public static String kebabToCamel(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpperCase = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    sb.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String camelToKebab(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}