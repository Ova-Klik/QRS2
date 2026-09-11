package com.techschool.attendance.service;

import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.service.mail.BrevoMailService;
import com.techschool.attendance.service.mail.EmailTemplateService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private EmailTemplateService emailTemplateService;

    @InjectMocks
    private BrevoMailService brevoMailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl dummySender = new JavaMailSenderImpl();
        mimeMessage = dummySender.createMimeMessage();

        emailTemplateService = new EmailTemplateService();
        ReflectionTestUtils.setField(emailTemplateService, "appFrontendUrl", "https://qrsattendance.netlify.app");

        brevoMailService = new BrevoMailService(javaMailSender, emailTemplateService);
        ReflectionTestUtils.setField(brevoMailService, "fromEmail", "noreply@qrsattendance.com");
        ReflectionTestUtils.setField(brevoMailService, "fromName", "QRAttendance");
    }

    @Test
    void testEmailTemplateServiceVerificationUrl() {
        String html = emailTemplateService.buildVerificationEmailHtml("John Doe", "test-token-123");
        assertNotNull(html);
        assertTrue(html.contains("Welcome, John Doe!"));
        assertTrue(html.contains("https://qrsattendance.netlify.app/verify-email?token=test-token-123"));
        assertTrue(html.contains("Verify Email Address"));
    }

    @Test
    void testEmailTemplateServicePasswordResetUrl() {
        String html = emailTemplateService.buildPasswordResetEmailHtml("Jane Doe", "reset-token-456");
        assertNotNull(html);
        assertTrue(html.contains("Hello, Jane Doe"));
        assertTrue(html.contains("https://qrsattendance.netlify.app/reset-password?token=reset-token-456"));
        assertTrue(html.contains("Reset Password"));
    }

    @Test
    void testSendVerificationEmailSuccess() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        brevoMailService.sendVerificationEmail("user@example.com", "User Test", "sample-vtoken");

        verify(javaMailSender, times(1)).createMimeMessage();
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendPasswordResetEmailSuccess() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        brevoMailService.sendPasswordResetEmail("user@example.com", "User Test", "sample-rtoken");

        verify(javaMailSender, times(1)).createMimeMessage();
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendEmailMailSenderExceptionHandled() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Brevo SMTP connection failed"))
                .when(javaMailSender).send(any(MimeMessage.class));

        AppException ex = assertThrows(AppException.class, () ->
                brevoMailService.sendVerificationEmail("user@example.com", "User Test", "token-xyz"));

        assertTrue(ex.getMessage().contains("Failed to deliver email"));
    }

    @Test
    void testEmptyRecipientEmailThrowsAppException() {
        assertThrows(AppException.class, () ->
                brevoMailService.sendVerificationEmail("", "User Test", "token-xyz"));
    }
}
