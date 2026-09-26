package com.techschool.attendance.service;

import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.request.AttendanceRequestDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.data.model.Attendance;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceScanService attendanceScanService;
    private final AttendanceCheckInService attendanceCheckInService;
    private final AttendanceAnalyticsService attendanceAnalyticsService;
    private final AttendanceFacilitatorReportService attendanceFacilitatorReportService;
    private final AttendanceManualAttendanceService attendanceManualAttendanceService;
    private final AttendanceExportService attendanceExportService;
    private final AttendanceMarkingService attendanceMarkingService;

    @Value("${app.attendance.timezone}")
    private String timezone;

    // ── QR Scan ──────────────────────────────────────────
    public QrResponseDto.ScanResponse scanQr(String studentId, QrRequestDto.ScanRequest request, String ipAddress) {
        return attendanceScanService.scanQr(studentId, request, ipAddress);
    }

    // ── Network & GPS Geofence Verification Logic ────────
    public String verifyCheckIn(QrRequestDto.ScanRequest request, String studentId, String remoteIp) {
        return attendanceCheckInService.verifyCheckIn(request, studentId, remoteIp);
    }

    // ── Manual Attendance ────────────────────────────────
    public AttendanceResponseDto.AttendanceRecord markManual(String actorId, String actorName, String actorRole,
                                                      AttendanceRequestDto.ManualMarkRequest request,
                                                      String ipAddress) {
        return attendanceMarkingService.markManual(actorId, actorName, actorRole, request, ipAddress);
    }

    // ── Queries ──────────────────────────────────────────
    public List<AttendanceResponseDto.AttendanceRecord> getStudentHistory(String studentId) {
        return attendanceAnalyticsService.getStudentHistory(studentId);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getStudentHistoryPage(
            String studentId, int page, int size) {
        return attendanceAnalyticsService.getStudentHistoryPage(studentId, page, size);
    }

    public AttendanceResponseDto.DailySummary getCohortSummaryToday(String cohortId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        return attendanceAnalyticsService.buildDailySummary(cohortId, today);
    }

    public AttendanceResponseDto.DailySummary buildDailySummary(String cohortId, LocalDate date) {
        return attendanceAnalyticsService.buildDailySummary(cohortId, date);
    }

    // ── Calendar ─────────────────────────────────────────

    public AnalyticsResponseDto.CalendarMonth buildCalendarMonth(String cohortId, int year, int month) {
        return attendanceAnalyticsService.buildCalendarMonth(cohortId, year, month);
    }

    public AnalyticsResponseDto.CalendarMonth buildStudentCalendarMonth(String studentId, int year, int month) {
        return attendanceAnalyticsService.buildStudentCalendarMonth(studentId, year, month);
    }

    // ── Attendance search by date ────────────────────────

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, int page, int size) {
        return attendanceAnalyticsService.searchByDate(cohortId, start, end, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, Integer lastNDays, int page, int size) {
        return attendanceAnalyticsService.searchByDate(cohortId, start, end, lastNDays, page, size);
    }

    /**
     * Resolves the effective search range. When {@code lastNDays} is provided the
     * range is [today-(n-1) .. today]; otherwise the explicit start/end is used.
     */
    public LocalDate[] resolveDateRange(LocalDate start, LocalDate end, Integer lastNDays) {
        return attendanceAnalyticsService.resolveDateRange(start, end, lastNDays);
    }

    /** Non-paginated list used for calendar / date-range exports. */
    public List<AttendanceResponseDto.AttendanceRecord> findRecordsInRange(String cohortId, LocalDate start, LocalDate end) {
        return attendanceAnalyticsService.findRecordsInRange(cohortId, start, end);
    }

    public List<AttendanceResponseDto.AttendanceRecord> findStudentRecordsInRange(String studentId, LocalDate start, LocalDate end) {
        return attendanceAnalyticsService.findStudentRecordsInRange(studentId, start, end);
    }

    /**
     * Builds a single-student summary export row (attendance %, present, absent,
     * late, excused, holiday, days attended/missed, streaks and rating).
     */
    public AnalyticsResponseDto.StudentAnalytics buildStudentSummaryExport(String studentId) {
        return attendanceAnalyticsService.buildStudentSummaryExport(studentId);
    }

    // ── Behaviour Analytics ──────────────────────────────

    public AnalyticsResponseDto.StudentAnalytics buildStudentAnalytics(String studentId) {
        return attendanceAnalyticsService.buildStudentAnalytics(studentId);
    }



    // ── Cohort export data ───────────────────────────────

    public List<AnalyticsResponseDto.CohortExportRow> buildCohortExportRows(String cohortId) {
        return attendanceAnalyticsService.buildCohortExportRows(cohortId);
    }

    // ── Helpers ──────────────────────────────────────────
    public AttendanceResponseDto.AttendanceRecord toRecord(Attendance a) {
        return attendanceAnalyticsService.toRecord(a);
    }

    /** Bulk record conversion with batched lookups to avoid N+1 queries. */
    public List<AttendanceResponseDto.AttendanceRecord> buildRecords(List<Attendance> records) {
        return attendanceAnalyticsService.buildRecords(records);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.ManualStudentAttendanceResponse> getManualAttendancePage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, int page, int size) {
        return attendanceManualAttendanceService.getManualAttendancePage(
            assignedCohortIds, cohortId, queryStr, targetDate, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, int page, int size) {
        return attendanceFacilitatorReportService.getFacilitatorReportPage(assignedCohortIds, cohortId, queryStr, targetDate, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, int page, int size) {
        return attendanceFacilitatorReportService.getFacilitatorReportPage(assignedCohortIds, cohortId, queryStr, targetDate, statusFilter, page, size);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportPublicProjectionReport(
            String cohortId, LocalDate targetDate, String format, String clientIp, ExportService exportService) {
        return attendanceExportService.exportPublicProjectionReport(cohortId, targetDate, format, clientIp, exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, LocalDate targetDate, String format, ExportService exportService) {
        return attendanceExportService.exportFacilitatorReport(assignedCohortIds, cohortId, targetDate, format, exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, String format, ExportService exportService) {
        return attendanceExportService.exportFacilitatorReport(assignedCohortIds, cohortId, queryStr, targetDate, statusFilter, format, exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            String actorId, List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, String format, String source, ExportService exportService) {
        return attendanceExportService.exportFacilitatorReport(
            actorId, assignedCohortIds, cohortId, queryStr, targetDate, statusFilter, format, source, exportService);
    }

    public static void sortFacilitatorAttendanceRecords(List<AttendanceResponseDto.AttendanceRecord> records) {
        records.sort((r1, r2) -> {
            Instant t1 = r1.getMarkedAt();
            Instant t2 = r2.getMarkedAt();

            // Primary sort: attendance timestamp ascending (earliest attendance first)
            if (t1 != null && t2 != null) {
                int cmp = t1.compareTo(t2);
                if (cmp != 0) return cmp;
            } else if (t1 != null) {
                return -1;
            } else if (t2 != null) {
                return 1;
            }

            // Secondary sort: student full name ascending (A-Z)
            String n1 = r1.getStudentName() != null ? r1.getStudentName() : "";
            String n2 = r2.getStudentName() != null ? r2.getStudentName() : "";
            return n1.compareToIgnoreCase(n2);
        });
    }

    public static boolean isAttendedRecord(AttendanceResponseDto.AttendanceRecord r) {
        if (r == null || r.getStatus() == null) return false;
        String status = r.getStatus().toUpperCase();
        return "PRESENT".equals(status) || "LATE".equals(status) || "EARLY".equals(status)
                || (r.getMarkedAt() != null && !"ABSENT".equals(status) && !"EXCUSED".equals(status) && !"WEEKEND".equals(status) && !"HOLIDAY".equals(status));
    }
}
