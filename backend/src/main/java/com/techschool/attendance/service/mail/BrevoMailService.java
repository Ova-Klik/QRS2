package com.techschool.attendance.service.mail;

import com.techschool.attendance.exception.AppException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String token) {
        String subject = "Verify Your QRAttendance Account";
        String htmlContent = emailTemplateService.buildVerificationEmailHtml(recipientName, token);
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    @Async
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

            log.info("Dispatching email via Brevo SMTP to recipient: {}", maskEmail(toEmail));
            javaMailSender.send(mimeMessage);
            log.info("Successfully sent email via Brevo SMTP to recipient: {}", maskEmail(toEmail));

        } catch (MessagingException e) {
            log.error("MessagingException while attempting to send email to {}: {}", maskEmail(toEmail), e.getMessage());
            throw AppException.internalServerError("Failed to compose or send email via mail service");
        } catch (MailException e) {
            log.error("MailException (SMTP connection error) while sending email to {}: {}", maskEmail(toEmail), e.getMessage());
            throw AppException.internalServerError("Failed to deliver email through mail service. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected exception while sending email to {}: {}", maskEmail(toEmail), e.getMessage());
            throw AppException.internalServerError("An unexpected error occurred while delivering email");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf("@");
        if (atIdx <= 2) return "***" + email.substring(atIdx);
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }
}
