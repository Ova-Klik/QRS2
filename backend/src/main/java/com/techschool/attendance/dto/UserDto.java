package com.techschool.attendance.dto;

import com.techschool.attendance.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class UserDto {

    @Data
    public static class CreateUserRequest {
        @NotBlank
        private String name;
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 6)
        private String password;
        @NotNull
        private User.Role role;
        private String cohortId;
        private String registrationNumber;
        private List<String> assignedCohortIds;
    }

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
        private AnalyticsDto.StudentAnalytics analytics;

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

    @Data
    public static class UpdateUserRequest {
        private String name;
        private String cohortId;
        private String registrationNumber;
        private List<String> assignedCohortIds;
        private Boolean active;
    }
}
