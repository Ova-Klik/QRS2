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

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CohortResponse {
        private String id;
        private String name;
        private String facilitatorId;
        private String facilitatorName;
        private String schedule;
        private boolean active;
        private int studentCount;
        private double attendanceRate;
        private Instant createdAt;
    }
}
