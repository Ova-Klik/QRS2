package com.techschool.attendance.dto;

import com.techschool.attendance.model.ExcuseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class ExcuseDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Reason is required")
        private String reason;

        @Min(value = 1, message = "Number of days must be at least 1")
        private int numberOfDays = 1;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotBlank(message = "Cover up plan is required")
        private String coverUpPlan;
    }

    @Data
    public static class ReviewRequest {
        @NotNull(message = "Status is required")
        private ExcuseRequest.Status status; // APPROVED or REJECTED

        private String notes;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String studentId;
        private String studentName;
        private String cohortId;
        private String reason;
        private int numberOfDays;
        private LocalDate startDate;
        private LocalDate endDate;
        private String coverUpPlan;
        private ExcuseRequest.Status status;
        private String reviewedById;
        private String reviewedByName;
        private String reviewerNotes;
        private Instant reviewedAt;
        private Instant createdAt;
    }
}
