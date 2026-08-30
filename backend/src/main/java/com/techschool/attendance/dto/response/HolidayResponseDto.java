package com.techschool.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class HolidayResponseDto {

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
