package com.techschool.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class UserResponseDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        private String id;
        private String name;
        private String email;
        private String phone;
        private String role;
        private String cohortId;
        private String cohortName;
        private String registrationNumber;
        private List<String> assignedCohortIds;
        private boolean active;
        private boolean biometricRegistered;
        private Instant createdAt;
        private DeviceInfo device;
        private AttendanceSummary attendanceSummary;
        private AnalyticsResponseDto.StudentAnalytics analytics;

        @Data @AllArgsConstructor @NoArgsConstructor
        public static class DeviceInfo {
            private String id;
            private String fingerprint;
            private boolean locked;
            private Instant registeredAt;
        }

        @Data @AllArgsConstructor @NoArgsConstructor
        public static class AttendanceSummary {
            private int total;
            private int present;
            private int late;
            private int absent;
            private int excused;
            private double rate;
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class StudentAttendanceResponse {
        private String id;
        private String name;
        private String registrationNumber;
        private String email;
        private String cohortId;
        private String cohortName;
        private double attendanceRate;
        private int presentDays;
        private int absentDays;
        private int excusedDays;
        private int lateDays;
        private int holidayCount;
        private int totalAttendanceDays;
        private String rating; // EXCELLENT, GOOD, FAIR, POOR
        private java.time.LocalDate lastAttendanceDate;
        private boolean active;
        private Instant createdAt;
    }
}
