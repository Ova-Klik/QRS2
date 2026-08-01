package com.techschool.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class AnalyticsDto {

    /** Generic paginated response wrapper used across admin list endpoints. */
    @Data @AllArgsConstructor
    public static class PageResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /** Per-student attendance aggregates produced by a single MongoDB aggregation. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StudentAttendanceStats {
        private String studentId;
        private long total;
        private long present;
        private long late;
        private long absent;
        private long excused;
        private long holiday;
    }

    /** Per-student behaviour analytics: streaks, counts, trend and overall rating. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StudentAnalytics {
        private String studentId;
        private String studentName;
        private String cohortId;
        private String cohortName;
        private double attendanceRate;
        private int totalRecords;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private int holiday;
        private int lateArrivals;
        private int longestAttendanceStreak;
        private int longestAbsenceStreak;
        private String rating; // EXCELLENT | GOOD | FAIR | POOR
        private List<MonthlyTrend> trend;

        @Data @AllArgsConstructor @NoArgsConstructor
        public static class MonthlyTrend {
            private String month;
            private int year;
            private double rate;
        }
    }

    /** A single day cell inside the attendance calendar. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CalendarDay {
        private LocalDate date;
        private boolean weekend;
        private boolean holiday;
        private String holidayName;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private int holidayCount;
        private int totalStudents;
    }

    /** Full month payload for the attendance calendar. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CalendarMonth {
        private int year;
        private int month;
        private String cohortId;
        private String cohortName;
        private List<CalendarDay> days;
    }

    /** One row in an entire-cohort attendance export. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CohortExportRow {
        private String studentName;
        private String registrationNumber;
        private double attendanceRate;
        private int present;
        private int late;
        private int excused;
        private int holiday;
        private int daysAttended;
        private int daysMissed;
        private int schoolDays;
    }
}
