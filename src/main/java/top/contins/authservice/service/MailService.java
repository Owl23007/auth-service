package top.contins.authservice.service;

import java.util.Map;

/**
 * 邮件服务接口
 */
public interface MailService {
    /**
     * 发送邮件
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    void sendEmail(String to, String subject, String content);

    /**
     * 使用模板发送邮件
     *
     * @param to           收件人邮箱
     * @param templateType 模板类型
     * @param placeholders 模板占位符参数
     */
    void sendEmailWithTemplate(String to, String templateType, Map<String, Object> placeholders);

    /**
     * 获取HTML内容
     *
     * @param templateType 模板类型
     * @param placeholders 模板占位符参数
     * @return 邮件内容
     */
    String getHTMLContent(String templateType, Map<String, Object> placeholders);
}