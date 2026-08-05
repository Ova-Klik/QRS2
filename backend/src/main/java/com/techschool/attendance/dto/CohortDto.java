package com.techschool.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class CohortDto {

    @Data
    public static class CreateCohortRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String facilitatorId;
        private String schedule;
        private String description;
    }

    @Data
    public static class UpdateCohortRequest {
        @NotBlank
        private String name;
        private String facilitatorId;
        private String schedule;
        private String description;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CohortResponse {
        private String id;
        private String name;
        private String facilitatorId;
        private String facilitatorName;
        private String facilitatorEmail;
        private String facilitatorPhone;
        private String schedule;
        private boolean active;
        private int studentCount;
        private double attendanceRate;
        private int presentCount;
        private int earlyCount;
        private int absentCount;
        private int excusedCount;
        private int lateCount;
        private int totalRecords;
        private double presentRate;
        private double absentRate;
        private double excusedRate;
        private double lateRate;
        private double averageDailyAttendance;
        private Instant createdAt;

        public CohortResponse(String id, String name, String facilitatorId, String facilitatorName,
                              String schedule, boolean active, int studentCount, double attendanceRate, Instant createdAt) {
            this.id = id;
            this.name = name;
            this.facilitatorId = facilitatorId;
            this.facilitatorName = facilitatorName;
            this.schedule = schedule;
            this.active = active;
            this.studentCount = studentCount;
            this.attendanceRate = attendanceRate;
            this.createdAt = createdAt;
        }
    }
}
