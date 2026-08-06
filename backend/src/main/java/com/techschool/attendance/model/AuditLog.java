package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String actorId;
    private String actorName;
    private String actorRole;

    private ActionType action;
    private String targetId;       // userId, deviceId, etc.
    private String targetName;
    private String detail;

    private Map<String, Object> metadata;
    private String ipAddress;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    public enum ActionType {
        // Auth
        LOGIN, LOGOUT, PASSWORD_RESET,
        // Attendance
        QR_GENERATED, QR_EXPIRED, ATTENDANCE_MARKED, ATTENDANCE_MANUAL_OVERRIDE,
        // Device
        DEVICE_REGISTERED, DEVICE_UNLOCKED, DEVICE_LOCKED,
        // User management
        USER_CREATED, USER_UPDATED, USER_DEACTIVATED, USER_DELETED,
        // Cohort
        COHORT_CREATED, COHORT_UPDATED, COHORT_TOGGLED, COHORT_DELETED, FACILITATOR_REASSIGNED,
        // Excuse Request
        EXCUSE_SUBMITTED, EXCUSE_REVIEWED,
        // Report Export
        PROJECTION_REPORT_DOWNLOADED
    }
}
