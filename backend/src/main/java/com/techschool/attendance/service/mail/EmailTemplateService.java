package com.techschool.attendance.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailTemplateService {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String appFrontendUrl;

    public String buildVerificationEmailHtml(String recipientName, String token) {
        String baseUrl = cleanUrl(appFrontendUrl);
        log.info("Constructing verification email link using base frontend URL: {}", baseUrl);
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "User";

        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verify Your QRAttendance Account</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f6f8; margin: 0; padding: 0; color: #1e293b; }
                    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; }
                    .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 32px; text-align: center; color: #ffffff; }
                    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                    .header p { margin: 6px 0 0 0; font-size: 13px; color: #94a3b8; }
                    .content { padding: 36px 32px; }
                    .greeting { font-size: 18px; font-weight: 600; margin-bottom: 16px; color: #0f172a; }
                    .message { font-size: 15px; line-height: 1.6; color: #475569; margin-bottom: 28px; }
                    .btn-wrapper { text-align: center; margin: 32px 0; }
                    .btn { display: inline-block; background-color: #dc2626; color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: 600; padding: 14px 32px; border-radius: 8px; box-shadow: 0 2px 8px rgba(220,38,38,0.3); transition: all 0.2s ease; }
                    .url-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px 16px; font-size: 13px; word-break: break-all; color: #64748b; margin-bottom: 24px; }
                    .expiry-notice { font-size: 13px; color: #64748b; background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px 16px; border-radius: 4px; margin-bottom: 24px; }
                    .security-notice { font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; padding-top: 20px; }
                    .footer { background-color: #f8fafc; padding: 20px 32px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>QRAttendance</h1>
                        <p>Smart Attendance Management System</p>
                    </div>
                    <div class="content">
                        <div class="greeting">Welcome, {{RECIPIENT_NAME}}!</div>
                        <div class="message">
                            Thank you for joining QRAttendance. To complete your account setup and unlock full access, please verify your email address.
                        </div>
                        <div class="btn-wrapper">
                            <a href="{{ACTION_URL}}" target="_blank" class="btn">Verify Email Address</a>
                        </div>
                        <div class="expiry-notice">
                            <strong>Note:</strong> This verification link will expire in 24 hours.
                        </div>
                        <div class="message" style="font-size:13px; margin-bottom: 8px;">
                            If the button above does not work, copy and paste the link below into your web browser:
                        </div>
                        <div class="url-box">{{ACTION_URL}}</div>
                        <div class="security-notice">
                            If you did not register for a QRAttendance account, you can safely ignore this email.
                        </div>
                    </div>
                    <div class="footer">
                        &copy; QRAttendance. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
            """;

        return template.replace("{{RECIPIENT_NAME}}", escapeHtml(name))
                       .replace("{{ACTION_URL}}", verifyUrl);
    }

    public String buildPasswordResetEmailHtml(String recipientName, String token) {
        String baseUrl = cleanUrl(appFrontendUrl);
        log.info("Constructing password reset email link using base frontend URL: {}", baseUrl);
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "User";

        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Reset Your QRAttendance Password</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f6f8; margin: 0; padding: 0; color: #1e293b; }
                    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; }
                    .header { background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); padding: 32px; text-align: center; color: #ffffff; }
                    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                    .header p { margin: 6px 0 0 0; font-size: 13px; color: #94a3b8; }
                    .content { padding: 36px 32px; }
                    .greeting { font-size: 18px; font-weight: 600; margin-bottom: 16px; color: #0f172a; }
                    .message { font-size: 15px; line-height: 1.6; color: #475569; margin-bottom: 28px; }
                    .btn-wrapper { text-align: center; margin: 32px 0; }
                    .btn { display: inline-block; background-color: #dc2626; color: #ffffff !important; text-decoration: none; font-size: 15px; font-weight: 600; padding: 14px 32px; border-radius: 8px; box-shadow: 0 2px 8px rgba(220,38,38,0.3); transition: all 0.2s ease; }
                    .url-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px 16px; font-size: 13px; word-break: break-all; color: #64748b; margin-bottom: 24px; }
                    .expiry-notice { font-size: 13px; color: #64748b; background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 4px; margin-bottom: 24px; }
                    .security-notice { font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; padding-top: 20px; }
                    .footer { background-color: #f8fafc; padding: 20px 32px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #f1f5f9; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>QRAttendance</h1>
                        <p>Smart Attendance Management System</p>
                    </div>
                    <div class="content">
                        <div class="greeting">Hello, {{RECIPIENT_NAME}}</div>
                        <div class="message">
                            We received a request to reset your password for your QRAttendance account. Click the button below to choose a new password.
                        </div>
                        <div class="btn-wrapper">
                            <a href="{{ACTION_URL}}" target="_blank" class="btn">Reset Password</a>
                        </div>
                        <div class="expiry-notice">
                            <strong>Note:</strong> This password reset link is valid for 1 hour.
                        </div>
                        <div class="message" style="font-size:13px; margin-bottom: 8px;">
                            If the button above does not work, copy and paste the link below into your web browser:
                        </div>
                        <div class="url-box">{{ACTION_URL}}</div>
                        <div class="security-notice">
                            <strong>Security Warning:</strong> If you did not request a password reset, please ignore this email or contact support if you have concerns. Your password remains unchanged.
                        </div>
                    </div>
                    <div class="footer">
                        &copy; QRAttendance. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
            """;

        return template.replace("{{RECIPIENT_NAME}}", escapeHtml(name))
                       .replace("{{ACTION_URL}}", resetUrl);
    }

    private String cleanUrl(String url) {
        if (url == null || url.isBlank()) return "http://localhost:5173";
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
