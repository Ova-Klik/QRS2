package com.techschool.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class CohortRequestDto {

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
}
