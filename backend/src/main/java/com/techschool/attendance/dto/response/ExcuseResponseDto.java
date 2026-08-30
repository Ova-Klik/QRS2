package com.techschool.attendance.dto.response;

import com.techschool.attendance.data.model.ExcuseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class ExcuseResponseDto {

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
