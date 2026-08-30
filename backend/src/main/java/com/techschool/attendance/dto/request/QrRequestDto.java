package com.techschool.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class QrRequestDto {

    @Data
    public static class GenerateRequest {
        @NotBlank
        private String cohortId;

        private Integer durationMinutes; // Optional custom duration in minutes
    }

    @Data
    public static class ScanRequest {
        @NotBlank
        private String token;
        private String deviceFingerprint;
        private String userAgent;
        // School network validation
        private String networkSSID;
        private String clientIP;
        // Geolocation Fallback
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        // Biometric
        private boolean biometricVerified;
        private String biometricCredentialId;
        private String biometricAuthenticatorData;
        private String biometricClientDataJSON;
        private String biometricSignature;
    }
}
