package com.techschool.attendance.service.mail;

public interface MailService {
    /**
     * Sends an email verification message to the user containing a verification URL token.
     *
     * @param recipientEmail User's email address
     * @param recipientName  User's full name
     * @param token          Verification token
     */
    void sendVerificationEmail(String recipientEmail, String recipientName, String token);

    /**
     * Sends a password reset email to the user containing a password reset URL token.
     *
     * @param recipientEmail User's email address
     * @param recipientName  User's full name
     * @param token          Password reset token
     */
    void sendPasswordResetEmail(String recipientEmail, String recipientName, String token);
}
