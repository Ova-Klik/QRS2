package com.techschool.attendance.service.mail;

import com.techschool.attendance.exception.AppException;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrevoMailService implements MailService {

    private final JavaMailSender javaMailSender;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.mail.from-email:noreply@qrsattendance.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:QRAttendance}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp-relay.brevo.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @PostConstruct
    public void validateMailConfigurationOnStartup() {
        if (isPlaceholderOrEmpty(mailUsername) || isPlaceholderOrEmpty(mailPassword)) {
            log.warn("⚠️ Brevo Mail Service Notice: BREVO_SMTP_USERNAME or BREVO_SMTP_PASSWORD is empty or set to placeholder default. Email delivery attempts will fail until valid Brevo SMTP credentials are provided.");
        } else {
            log.info("Brevo Mail Service initialized with host {}:{} and username {}", mailHost, mailPort, maskString(mailUsername));
        }
    }

    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String token) {
        String subject = "Verify Your QRAttendance Account";
        String htmlContent = emailTemplateService.buildVerificationEmailHtml(recipientEmail, recipientName, token);
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String token) {
        String subject = "Reset Your QRAttendance Password";
        String htmlContent = emailTemplateService.buildPasswordResetEmailHtml(recipientName, token);
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.error("Failed to send email: recipient email address is empty");
            throw AppException.badRequest("Recipient email address cannot be empty");
        }

        if (isPlaceholderOrEmpty(mailUsername) || isPlaceholderOrEmpty(mailPassword)) {
            log.error("Failed to send email to {}: Brevo SMTP credentials are not configured (username='{}', password='{}')",
                    maskEmail(toEmail), mailUsername, maskString(mailPassword));
            throw AppException.internalServerError("Email delivery failed: Brevo SMTP credentials (BREVO_SMTP_USERNAME / BREVO_SMTP_PASSWORD) are missing or set to placeholder defaults.");
        }

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            log.info("Dispatching email via Brevo SMTP [{}:{}] to recipient: {}", mailHost, mailPort, maskEmail(toEmail));
            javaMailSender.send(mimeMessage);
            log.info("Successfully sent email via Brevo SMTP to recipient: {}", maskEmail(toEmail));

        } catch (MessagingException e) {
            log.error("MessagingException while composing/sending email to {}: {}", maskEmail(toEmail), e.getMessage(), e);
            throw AppException.internalServerError("Failed to compose or send email via mail service: " + e.getMessage());
        } catch (MailException e) {
            log.error("MailException (SMTP connection/auth error) while sending email to {} via {}:{}: {}",
                    maskEmail(toEmail), mailHost, mailPort, e.getMessage(), e);
            throw AppException.internalServerError("Failed to deliver email through mail service (" + e.getMessage() + "). Please verify SMTP credentials and server connectivity.");
        } catch (Exception e) {
            log.error("Unexpected exception while sending email to {}: {}", maskEmail(toEmail), e.getMessage(), e);
            throw AppException.internalServerError("An unexpected error occurred while delivering email: " + e.getMessage());
        }
    }

    private boolean isPlaceholderOrEmpty(String val) {
        if (val == null || val.isBlank()) return true;
        String trimmed = val.trim().toLowerCase();
        return trimmed.contains("your-brevo-smtp-username") || trimmed.contains("your-brevo-smtp-key") || trimmed.equals("changeme");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf("@");
        if (atIdx <= 2) return "***" + email.substring(atIdx);
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }

    private String maskString(String str) {
        if (str == null || str.isBlank()) return "[empty]";
        if (str.length() <= 4) return "****";
        return str.substring(0, 3) + "****" + str.substring(str.length() - 1);
    }
}
