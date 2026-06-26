package com.xs.sheepaimall.util;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
/**
 * 邮箱发送工具
 */
@Slf4j
@Component
public class EmailUtil {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 发送6位数字验证码
     *
     * @param to   收件邮箱
     * @param code 验证码
     */
    public void sendCode(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("SheepAIMall - 邮箱验证码");

            String html = buildCodeHtml(code);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("邮箱验证码已发送 → {}, code={}", to, code);
        } catch (Exception e) {
            log.error("邮箱验证码发送失败 → {}: {}", to, e.getMessage());
            throw new RuntimeException("验证码发送失败，请检查邮箱地址或稍后重试");
        }
    }

    /** 构建验证码 HTML 邮件模板 */
    private String buildCodeHtml(String code) {
        return """
                <div style="max-width:500px;margin:20px auto;font-family:'Microsoft YaHei',sans-serif;border:1px solid #eee;border-radius:8px;overflow:hidden">
                    <div style="background:#667eea;padding:20px;text-align:center">
                        <span style="color:#fff;font-size:20px;font-weight:bold">SheepAIMall</span>
                    </div>
                    <div style="padding:30px">
                        <p style="font-size:14px;color:#333">您好，</p>
                        <p style="font-size:14px;color:#333">您的SheepAiMall邮箱验证码为：</p>
                        <div style="text-align:center;margin:25px 0">
                            <span style="display:inline-block;font-size:36px;font-weight:bold;color:#667eea;letter-spacing:8px;background:#f5f7ff;padding:12px 30px;border-radius:6px">%s</span>
                        </div>
                        <p style="font-size:12px;color:#999">验证码有效期为5分钟，请勿泄露给他人。</p>
                        <p style="font-size:12px;color:#999">如非本人操作，请忽略此邮件。</p>
                        <hr style="border:none;border-top:1px solid #eee;margin:20px 0">
                        <p style="font-size:11px;color:#ccc;text-align:center">此邮件由系统自动发送，请勿回复</p>
                    </div>
                </div>
                """.formatted(code);
    }
}
