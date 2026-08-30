package com.techschool.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AttendanceResponseDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AttendanceRecord {
        private String id;
        private String studentId;
        private String studentName;
        private String registrationNumber;
        private String cohortId;
        private String cohortName;
        private LocalDate date;
        private Instant markedAt;
        private String status;
        private boolean manual;
        private String manualReason;
        private String deviceUsed;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ManualStudentAttendanceResponse {
        private String studentId;
        private String studentName;
        private String registrationNumber;
        private String email;
        private String cohortId;
        private String cohortName;
        private LocalDate date;
        private String status;
        private Instant markedAt;
        private boolean manual;
        private String manualReason;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DailySummary {
        private LocalDate date;
        private String cohortId;
        private String cohortName;
        private int total;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private int holiday;
        private int manual;
        private double rate;
        private List<AttendanceRecord> records;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StudentHistory {
        private String studentId;
        private String studentName;
        private int total;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private double rate;
        private List<AttendanceRecord> records;
    }
}
