package com.techschool.attendance.dto.request;

import com.techschool.attendance.data.model.ExcuseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

public class ExcuseRequestDto {

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
}
