package com.techschool.attendance.service;

import com.techschool.attendance.dto.AuthDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import com.techschool.attendance.security.JwtUtils;
import com.techschool.attendance.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;
    private final MailService mailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.network.school-wifi-ssid:TechSchool-WiFi}")
    private String schoolWifiSsid;

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request, String ipAddress) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> AppException.unauthorized("User does not exist. Kindly register below."));

        if (!user.isActive()) {
            throw AppException.unauthorized("Account is deactivated. Contact admin.");
        }

        if (!user.isEmailVerified()) {
            throw AppException.unauthorized("Email is not verified. Please check your inbox or request a new verification link.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AppException.unauthorized("Invalid email or password");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        auditService.log(user.getId(), user.getName(), user.getRole().name(),
                AuditLog.ActionType.LOGIN, null, null, "Login from " + ipAddress, ipAddress);

        return new AuthDto.LoginResponse(
                token, user.getId(), user.getName(),
                user.getEmail(), user.getRole().name(), user.getCohortId()
        );
    }

    // ── Self-Registration ────────────────────────────────

    public AuthDto.LoginResponse registerStudent(AuthDto.RegisterStudentRequest request, String ipAddress) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("Email already registered: " + request.getEmail());
        }

        // Resolve cohort by number (e.g. "29" -> finds "Cohort 29")
        Cohort cohort = cohortRepository.findByActive(true).stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase()
                        .contains("cohort " + request.getCohortNumber().trim().toLowerCase()))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("Cohort " + request.getCohortNumber() + " not found or inactive"));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.STUDENT);
        user.setCohortId(cohort.getId());
        user.setActive(true);
        user.setEmailVerified(false);

        String vToken = generateSecureToken();
        user.setVerificationToken(vToken);
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));

        User saved = userRepository.save(user);

        // Send verification email via MailService abstraction
        try {
            mailService.sendVerificationEmail(saved.getEmail(), saved.getName(), vToken);
        } catch (Exception e) {
            log.error("Failed to send verification email during student registration for {}: {}", saved.getEmail(), e.getMessage());
        }

        auditService.log(saved.getId(), saved.getName(), "STUDENT",
                AuditLog.ActionType.USER_CREATED, saved.getId(), saved.getName(),
                "Self-registration as student in " + cohort.getName(), ipAddress);

        return new AuthDto.LoginResponse(
                null, saved.getId(), saved.getName(),
                saved.getEmail(), saved.getRole().name(), saved.getCohortId()
        );
    }

    public AuthDto.LoginResponse registerFacilitator(AuthDto.RegisterFacilitatorRequest request, String ipAddress) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("Email already registered: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.Role.FACILITATOR);
        user.setActive(true);
        user.setEmailVerified(false);

        String vToken = generateSecureToken();
        user.setVerificationToken(vToken);
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));

        User saved = userRepository.save(user);

        // Send verification email via MailService abstraction
        try {
            mailService.sendVerificationEmail(saved.getEmail(), saved.getName(), vToken);
        } catch (Exception e) {
            log.error("Failed to send verification email during facilitator registration for {}: {}", saved.getEmail(), e.getMessage());
        }

        auditService.log(saved.getId(), saved.getName(), "FACILITATOR",
                AuditLog.ActionType.USER_CREATED, saved.getId(), saved.getName(),
                "Self-registration as facilitator", ipAddress);

        return new AuthDto.LoginResponse(
                null, saved.getId(), saved.getName(),
                saved.getEmail(), saved.getRole().name(), null
        );
    }

    // ── Email Verification & Resend ───────────────────────

    public AuthDto.MessageResponse verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw AppException.badRequest("Verification token is required");
        }
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> AppException.badRequest("Invalid or expired verification token"));

        if (user.getVerificationTokenExpiry() == null || user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw AppException.badRequest("Verification token has expired. Please request a new verification email.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        auditService.log(user.getId(), user.getName(), user.getRole().name(),
                AuditLog.ActionType.USER_UPDATED, user.getId(), user.getName(),
                "Email verified successfully", null);

        return new AuthDto.MessageResponse("Email verified successfully. You can now log in.");
    }

    public AuthDto.MessageResponse resendVerificationEmail(String email) {
        if (email == null || email.isBlank()) {
            throw AppException.badRequest("Email address is required");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> AppException.notFound("User with specified email not found"));

        if (user.isEmailVerified()) {
            return new AuthDto.MessageResponse("Your email address is already verified. Please log in.");
        }

        String vToken = generateSecureToken();
        user.setVerificationToken(vToken);
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        mailService.sendVerificationEmail(user.getEmail(), user.getName(), vToken);

        return new AuthDto.MessageResponse("Verification email has been resent. Please check your inbox.");
    }

    // ── Password Reset (Email Token Based) ─────────────────

    public AuthDto.MessageResponse forgotPassword(AuthDto.ForgotPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw AppException.badRequest("Email address is required");
        }

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase()).orElse(null);
        if (user != null && user.isActive()) {
            String resetToken = generateSecureToken();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
            userRepository.save(user);

            try {
                mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetToken);
            } catch (Exception e) {
                log.error("Failed to send password reset email for user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        return new AuthDto.MessageResponse("If an account exists with that email, a password reset link has been sent.");
    }

    public AuthDto.MessageResponse resetPasswordWithToken(AuthDto.ResetPasswordWithTokenRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw AppException.badRequest("Password reset token is required");
        }

        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> AppException.badRequest("Invalid or expired password reset token"));

        if (user.getPasswordResetTokenExpiry() == null || user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw AppException.badRequest("Password reset token has expired. Please request a new password reset link.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        user.setPasswordResetRequired(false);
        userRepository.save(user);

        auditService.log(user.getId(), user.getName(), user.getRole().name(),
                AuditLog.ActionType.PASSWORD_RESET, user.getId(), user.getName(),
                "Password reset successfully via email token", null);

        return new AuthDto.MessageResponse("Your password has been reset successfully. You can now log in with your new password.");
    }

    // ── WebAuthn Biometric ───────────────────────────────

    public AuthDto.ChallengeResponse generateBiometricChallenge(String userId) {
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        return new AuthDto.ChallengeResponse(challenge, "Tech School", "localhost");
    }

    public void registerBiometric(String userId, AuthDto.WebAuthnRegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        user.setWebAuthnCredentialId(request.getCredentialId());
        user.setWebAuthnPublicKey(request.getPublicKey());
        userRepository.save(user);

        auditService.log(userId, user.getName(), user.getRole().name(),
                AuditLog.ActionType.DEVICE_REGISTERED, userId, user.getName(),
                "Biometric credential registered", null);
    }

    public boolean verifyBiometric(String userId, AuthDto.WebAuthnVerifyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (user.getWebAuthnCredentialId() == null) {
            throw AppException.badRequest("No biometric credential registered. Please register your fingerprint first.");
        }

        if (!user.getWebAuthnCredentialId().equals(request.getCredentialId())) {
            log.warn("Biometric credential mismatch for user {} — expected {} got {}",
                    userId, user.getWebAuthnCredentialId(), request.getCredentialId());
            throw AppException.forbidden("Biometric credential does not match registered device.");
        }

        return true;
    }

    // ── Password Management ──────────────────────────────

    public void changePassword(String userId, AuthDto.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetRequired(false);
        userRepository.save(user);
        auditService.log(userId, user.getName(), user.getRole().name(),
                AuditLog.ActionType.PASSWORD_RESET, userId, user.getName(), "Password changed by user", null);
    }

    public void adminResetPassword(String adminId, AuthDto.ResetPasswordRequest request) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> AppException.notFound("Admin not found"));
        User target = userRepository.findById(request.getUserId())
                .orElseThrow(() -> AppException.notFound("Target user not found"));

        target.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        target.setPasswordResetRequired(true);
        userRepository.save(target);

        auditService.log(adminId, admin.getName(), admin.getRole().name(),
                AuditLog.ActionType.PASSWORD_RESET, target.getId(), target.getName(),
                "Password reset by admin", null);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
