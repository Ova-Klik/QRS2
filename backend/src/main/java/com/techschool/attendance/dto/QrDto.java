package com.techschool.attendance.dto;

import com.techschool.attendance.model.Attendance;
import com.techschool.attendance.model.QrSession;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class QrDto {

    @Data
    public static class GenerateRequest {
        @NotBlank
        private String cohortId;

        private Integer durationMinutes; // Optional custom duration in minutes
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class QrResponse {
        private String sessionId;
        private String cohortId;
        private String cohortName;
        private String qrImageBase64;
        private String token;
        private Instant activeFrom;
        private Instant expiresAt;
        private QrSession.SessionState state;
        private long remainingSeconds;
        private Integer refreshInterval;
        private Boolean refreshEnabled;

        public QrResponse(String sessionId, String cohortId, String cohortName,
                          String qrImageBase64, String token,
                          Instant activeFrom, Instant expiresAt,
                          QrSession.SessionState state, long remainingSeconds) {
            this.sessionId = sessionId;
            this.cohortId = cohortId;
            this.cohortName = cohortName;
            this.qrImageBase64 = qrImageBase64;
            this.token = token;
            this.activeFrom = activeFrom;
            this.expiresAt = expiresAt;
            this.state = state;
            this.remainingSeconds = remainingSeconds;
            this.refreshInterval = 15;
            this.refreshEnabled = true;
        }
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

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ScanResponse {
        private boolean success;
        private String message;
        private Attendance.AttendanceStatus status;
        private Instant markedAt;
    }
}
