package com.techschool.attendance.controller;

import com.techschool.attendance.dto.*;
import com.techschool.attendance.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/facilitator")
@PreAuthorize("hasAnyRole('FACILITATOR','SUPER_ADMIN')")
@RequiredArgsConstructor
public class FacilitatorController {

    private final QrService qrService;
    private final AttendanceService attendanceService;
    private final CohortService cohortService;
    private final UserService userService;
    private final ExcuseService excuseService;
    private final ExportService exportService;

    // ── QR ────────────────────────────────────────────────

    @PostMapping("/qr/generate")
    public ResponseEntity<QrDto.QrResponse> generateQr(
            @AuthenticationPrincipal String facId,
            @Valid @RequestBody QrDto.GenerateRequest request,
            @RequestParam(required = false) String origin) throws Exception {
        var fac = userService.getById(facId);
        return ResponseEntity.ok(
                qrService.generateSession(facId, fac.getName(), request.getCohortId(), request.getDurationMinutes(), origin));
    }

    @GetMapping("/qr/active/{cohortId}")
    public ResponseEntity<QrDto.QrResponse> getActiveQr(
            @PathVariable String cohortId,
            @RequestParam(required = false) String origin) throws Exception {
        return ResponseEntity.ok(qrService.getActiveSession(cohortId, origin));
    }

    @PostMapping("/qr/expire/{sessionId}")
    public ResponseEntity<Void> expireQr(
            @AuthenticationPrincipal String facId,
            @PathVariable String sessionId) {
        var fac = userService.getById(facId);
        qrService.expireSession(sessionId, facId, fac.getName());
        return ResponseEntity.ok().build();
    }

    // ── Attendance ────────────────────────────────────────

    @PostMapping("/attendance/manual")
    public ResponseEntity<AttendanceDto.AttendanceRecord> manualAttendance(
            @AuthenticationPrincipal String facId,
            @Valid @RequestBody AttendanceDto.ManualMarkRequest request,
            HttpServletRequest http) {
        var fac = userService.getById(facId);
        return ResponseEntity.ok(attendanceService.markManual(
                facId, fac.getName(), fac.getRole(), request, http.getRemoteAddr()));
    }

    @GetMapping("/attendance/today/{cohortId}")
    public ResponseEntity<AttendanceDto.DailySummary> todaySummary(
            @PathVariable String cohortId) {
        return ResponseEntity.ok(attendanceService.getCohortSummaryToday(cohortId));
    }

    @GetMapping("/attendance/search")
    public ResponseEntity<AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord>> searchAttendance(
            @RequestParam String cohortId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Integer lastNDays,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        return ResponseEntity.ok(attendanceService.searchByDate(cohortId, start, end, lastNDays, page, size));
    }

    @GetMapping("/attendance/calendar")
    public ResponseEntity<AnalyticsDto.CalendarMonth> calendar(
            @RequestParam String cohortId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        java.time.YearMonth ym = (year == null || month == null)
                ? java.time.YearMonth.now() : java.time.YearMonth.of(year, month);
        return ResponseEntity.ok(attendanceService.buildCalendarMonth(cohortId, ym.getYear(), ym.getMonthValue()));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam String cohortId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Integer lastNDays,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        LocalDate[] range = attendanceService.resolveDateRange(start, end, lastNDays);
        LocalDate effStart = range[0];
        LocalDate effEnd = range[1];
        List<AttendanceDto.AttendanceRecord> rows =
                attendanceService.findRecordsInRange(cohortId, effStart, effEnd);
        List<List<Object>> table = new java.util.ArrayList<>();
        for (AttendanceDto.AttendanceRecord r : rows) {
            table.add(List.of(
                    r.getStudentName() != null ? r.getStudentName() : "",
                    r.getStudentId() != null ? r.getStudentId() : "",
                    r.getDate() != null ? r.getDate().toString() : "",
                    r.getStatus() != null ? r.getStatus() : "",
                    r.isManual() ? "MANUAL" : "QR",
                    r.getDeviceUsed() != null ? r.getDeviceUsed() : ""));
        }
        return exportService.export(
                List.of("Student Name", "Student ID", "Date", "Status", "Source", "Device"),
                table, format, "cohort_" + cohortId + "_attendance");
    }

    // ── Excuse Requests ───────────────────────────────────

    @GetMapping("/excuse-requests/{cohortId}")
    public ResponseEntity<List<ExcuseDto.Response>> getCohortExcuseRequests(
            @PathVariable String cohortId) {
        return ResponseEntity.ok(excuseService.getCohortRequests(cohortId));
    }

    @PatchMapping("/excuse-requests/{requestId}/review")
    public ResponseEntity<ExcuseDto.Response> reviewExcuseRequest(
            @AuthenticationPrincipal String facId,
            @PathVariable String requestId,
            @Valid @RequestBody ExcuseDto.ReviewRequest request) {
        var fac = userService.getById(facId);
        return ResponseEntity.ok(excuseService.reviewRequest(facId, fac.getName(), fac.getRole(), requestId, request));
    }

    // ── Cohorts ───────────────────────────────────────────

    @GetMapping("/cohorts")
    public ResponseEntity<List<CohortDto.CohortResponse>> myCohorts(
            @AuthenticationPrincipal String facId) {
        return ResponseEntity.ok(cohortService.getCohortsByFacilitator(facId));
    }

    // ── Settings ──────────────────────────────────────────

    @GetMapping("/settings/network")
    public ResponseEntity<java.util.Map<String, String>> getSettings() {
        return ResponseEntity.ok(userService.getNetworkSettings());
    }

    @PutMapping("/settings/network")
    public ResponseEntity<java.util.Map<String, String>> updateSettings(
            @AuthenticationPrincipal String facId,
            @RequestBody java.util.Map<String, String> settings) {
        var fac = userService.getById(facId);
        return ResponseEntity.ok(userService.updateNetworkSettings(facId, fac.getName(), settings));
    }

    @GetMapping("/attendance/manual-list")
    public ResponseEntity<AnalyticsDto.PageResponse<AttendanceDto.ManualStudentAttendanceResponse>> getManualAttendanceList(
            @AuthenticationPrincipal String facId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var myCohorts = cohortService.getCohortsByFacilitator(facId);
        var assignedCohortIds = myCohorts.stream().map(CohortDto.CohortResponse::getId).collect(Collectors.toList());
        return ResponseEntity.ok(attendanceService.getManualAttendancePage(assignedCohortIds, cohortId, q, date, page, size));
    }

    @GetMapping("/attendance/reports")
    public ResponseEntity<AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord>> getReports(
            @AuthenticationPrincipal String facId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var myCohorts = cohortService.getCohortsByFacilitator(facId);
        var assignedCohortIds = myCohorts.stream().map(CohortDto.CohortResponse::getId).collect(Collectors.toList());
        return ResponseEntity.ok(attendanceService.getFacilitatorReportPage(assignedCohortIds, cohortId, q, date, page, size));
    }

    @GetMapping("/attendance/reports/export")
    public ResponseEntity<byte[]> exportFacilitatorReport(
            @AuthenticationPrincipal String facId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "csv") String format) {
        var myCohorts = cohortService.getCohortsByFacilitator(facId);
        var assignedCohortIds = myCohorts.stream().map(CohortDto.CohortResponse::getId).collect(Collectors.toList());
        return attendanceService.exportFacilitatorReport(assignedCohortIds, cohortId, date, format, exportService);
    }

    // ── Dashboard ─────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto.FacilitatorStats> dashboard(
            @AuthenticationPrincipal String facId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) throws Exception {
        return ResponseEntity.ok(cohortService.buildFacilitatorStats(facId, cohortId, q, date, page, size));
    }
}
