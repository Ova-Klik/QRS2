package com.techschool.attendance.dto.response;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.QrSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class QrResponseDto {

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

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ScanResponse {
        private boolean success;
        private String message;
        private Attendance.AttendanceStatus status;
        private Instant markedAt;
        private String verificationMethod; // "WIFI", "GEOLOCATION", "UNVERIFIED"

        public ScanResponse(boolean success, String message, Attendance.AttendanceStatus status, Instant markedAt) {
            this.success = success;
            this.message = message;
            this.status = status;
            this.markedAt = markedAt;
            this.verificationMethod = "UNVERIFIED";
        }
    }
}
