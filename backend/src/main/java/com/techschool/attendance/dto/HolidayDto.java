package com.techschool.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class HolidayDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Holiday name is required")
        private String name;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;

        private String reason;

        /** true = all cohorts, false = only {@link #cohortId} */
        private boolean appliesToAll = true;

        private String cohortId;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private String reason;
        private Boolean appliesToAll;
        private String cohortId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private String reason;
        private boolean appliesToAll;
        private String cohortId;
        private String cohortName;
        private boolean active;
        private Instant createdAt;
    }
}
