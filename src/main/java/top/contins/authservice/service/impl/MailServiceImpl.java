package top.contins.authservice.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import top.contins.authservice.config.MailTemplateConfig;
import top.contins.authservice.service.MailService;
import top.contins.authservice.util.MailContentUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MailServiceImpl implements MailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private MailTemplateConfig mailTemplateConfig;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendEmail(String to, String subject, String content) {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            javaMailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    @Override
    public void sendEmailWithTemplate(String to, String templateType, Map<String, Object> placeholders) {
        String templateContent = mailTemplateConfig.getTemplateContent(templateType);

        // 添加通用占位符
        addCommonPlaceholders(placeholders);

        String emailContent = processTemplate(templateContent, placeholders);
        sendEmail(to, getSubjectByTemplateType(templateType), emailContent);
    }

    @Override
    public String getHTMLContent(String templateType, Map<String, Object> placeholders) {
        String templateContent = mailTemplateConfig.getTemplateContent(templateType);
        // 添加通用占位符
        addCommonPlaceholders(placeholders);

        return processTemplate(templateContent, placeholders);
    }

    /**
     * 添加通用占位符
     * 
     * @param placeholders 占位符映射
     */
    private void addCommonPlaceholders(Map<String, Object> placeholders) {
        // 如果没有设置发送时间，则添加当前时间
        if (!placeholders.containsKey("sendTime")) {
            placeholders.put("sendTime", MailContentUtil.getCurrentTime());
        }

        // 如果没有设置过期时间，则添加默认过期时间
        if (!placeholders.containsKey("expirationTime")) {
            placeholders.put("expirationTime", MailContentUtil.getExpirationTime(null));
        }
    }

    /**
     * 处理模板中的占位符替换
     * 
     * @param template     模板内容
     * @param placeholders 占位符映射
     * @return 替换后的邮件内容
     */
    private String processTemplate(String template, Map<String, Object> placeholders) {

        // 使用正则表达式查找并替换 ${key} 格式的占位符
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher matcher = pattern.matcher(template);

        // 使用 StringBuffer 来构建结果
        StringBuilder buffer = new StringBuilder();



        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = String.valueOf(placeholders.getOrDefault(placeholder, "${" + placeholder + "}"));

            replacement = HtmlUtils.htmlEscape(replacement);
            // 转义替换文本中的特殊字符
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * 根据模板类型获取邮件主题
     * 
     * @param templateType 模板类型
     * @return 邮件主题
     */
    private String getSubjectByTemplateType(String templateType) {
        return switch (templateType) {
            case "register" -> "欢迎注册我们的服务";
            case "resetPassword" -> "密码重置请求";
            default -> "系统通知";
        };
    }
}