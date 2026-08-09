package com.techschool.attendance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDto {

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 6)
        private String password;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class LoginResponse {
        private String token;
        private String userId;
        private String name;
        private String email;
        private String role;
        private String cohortId;
    }

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class ResetPasswordWithTokenRequest {
        @NotBlank
        private String token;
        @NotBlank @Size(min = 6)
        private String newPassword;
    }

    @Data
    public static class VerifyEmailRequest {
        @NotBlank
        private String token;
    }

    @Data
    public static class ResendVerificationRequest {
        @NotBlank @Email
        private String email;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String userId;
        @NotBlank @Size(min = 6)
        private String newPassword;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank @Size(min = 6)
        private String newPassword;
    }

    @Data
    public static class RegisterStudentRequest {
        @NotBlank private String name;
        @NotBlank @Email private String email;
        @NotBlank private String phone;
        @NotBlank @Size(min = 6) private String password;
        @NotBlank private String cohortNumber;
    }

    @Data
    public static class RegisterFacilitatorRequest {
        @NotBlank private String name;
        @NotBlank @Email private String email;
        @NotBlank private String phone;
        @NotBlank @Size(min = 6) private String password;
    }

    @Data
    public static class WebAuthnRegisterRequest {
        @NotBlank private String credentialId;
        @NotBlank private String publicKey;
    }

    @Data
    public static class WebAuthnVerifyRequest {
        @NotBlank private String credentialId;
        @NotBlank private String authenticatorData;
        @NotBlank private String clientDataJSON;
        @NotBlank private String signature;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ChallengeResponse {
        private String challenge;
        private String rpName;
        private String rpId;
    }
}
