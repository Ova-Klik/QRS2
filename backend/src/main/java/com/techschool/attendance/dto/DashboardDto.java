package com.techschool.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DashboardDto {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AdminStats {
        private int totalStudents;
        private int totalFacilitators;
        private int activeCohorts;
        private int presentToday;
        private int lateToday;
        private int absentToday;
        private int excusedToday;
        private int holidayToday;
        private int totalExcusedAllTime;
        private double schoolAttendanceRate;
        private List<CohortDto.CohortResponse> cohorts;
        private List<Map<String, Object>> recentActivity;
        private List<BehaviourInsight> studentBehaviour;
        private Map<String, Map<String, Integer>> dayOfWeekBreakdown;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BehaviourInsight {
        private String studentId;
        private String studentName;
        private String cohortName;
        private int totalRecords;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private double attendanceRate;
        private double lateRate;
        private String behaviorTag; // EXCELLENT, CHRONIC_LATE, CHRONIC_ABSENT, HIGH_EXCUSES, GOOD_STANDING
        private String behaviorInsightText;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FacilitatorStats {
        private int totalStudents;
        private int presentToday;
        private int lateToday;
        private int absentToday;
        private int excusedToday;
        private boolean qrSessionActive;
        private QrDto.QrResponse activeSession;
        private List<AttendanceDto.AttendanceRecord> todayRecords;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StudentStats {
        private int total;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private double rate;
        private boolean markedToday;
        private String todayStatus;
        private List<AttendanceDto.AttendanceRecord> recentHistory;
        private DeviceStatus deviceStatus;

        @Data @AllArgsConstructor @NoArgsConstructor
        public static class DeviceStatus {
            private boolean registered;
            private String fingerprint;
            private Instant registeredAt;
        }
    }
}
