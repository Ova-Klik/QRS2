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
import java.util.Map;

@RestController
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('STUDENT','FACILITATOR','SUPER_ADMIN')")
@RequiredArgsConstructor
public class StudentController {

    private final AttendanceService attendanceService;
    private final UserService userService;
    private final CohortService cohortService;
    private final ExcuseService excuseService;
    private final ExportService exportService;

    @PostMapping("/attendance/scan")
    public ResponseEntity<QrDto.ScanResponse> scan(
            @AuthenticationPrincipal String studentId,
            @Valid @RequestBody QrDto.ScanRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(
                attendanceService.scanQr(studentId, request, http.getRemoteAddr()));
    }

    @GetMapping("/attendance/history")
    public ResponseEntity<List<AttendanceDto.AttendanceRecord>> history(
            @AuthenticationPrincipal String studentId) {
        return ResponseEntity.ok(attendanceService.getStudentHistory(studentId));
    }

    @GetMapping("/attendance/history/page")
    public ResponseEntity<AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord>> historyPage(
            @AuthenticationPrincipal String studentId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(attendanceService.getStudentHistoryPage(studentId, page, size));
    }

    @GetMapping("/attendance/analytics")
    public ResponseEntity<AnalyticsDto.StudentAnalytics> myAnalytics(
            @AuthenticationPrincipal String studentId) {
        return ResponseEntity.ok(attendanceService.buildStudentAnalytics(studentId));
    }

    @GetMapping("/attendance/calendar")
    public ResponseEntity<AnalyticsDto.CalendarMonth> myCalendar(
            @AuthenticationPrincipal String studentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        java.time.YearMonth ym = (year == null || month == null)
                ? java.time.YearMonth.now() : java.time.YearMonth.of(year, month);
        return ResponseEntity.ok(attendanceService.buildStudentCalendarMonth(studentId, ym.getYear(), ym.getMonthValue()));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportMyAttendance(
            @AuthenticationPrincipal String studentId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        LocalDate effStart = start != null ? start : LocalDate.now().minusMonths(1);
        LocalDate effEnd = end != null ? end : LocalDate.now();
        List<AttendanceDto.AttendanceRecord> rows =
                attendanceService.findStudentRecordsInRange(studentId, effStart, effEnd);
        List<List<Object>> table = new java.util.ArrayList<>();
        for (AttendanceDto.AttendanceRecord r : rows) {
            table.add(List.of(
                    r.getDate() != null ? r.getDate().toString() : "",
                    r.getStatus() != null ? r.getStatus() : "",
                    r.isManual() ? "MANUAL" : "QR",
                    r.getDeviceUsed() != null ? r.getDeviceUsed() : "",
                    r.getManualReason() != null ? r.getManualReason() : ""));
        }
        return exportService.export(
                List.of("Date", "Status", "Source", "Device", "Reason"),
                table, format, "my_attendance_" + effStart + "_to_" + effEnd);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto.StudentStats> dashboard(
            @AuthenticationPrincipal String studentId) {
        return ResponseEntity.ok(cohortService.buildStudentStats(studentId));
    }

    @PostMapping("/device/register")
    public ResponseEntity<UserDto.UserResponse.DeviceInfo> registerOwnDevice(
            @AuthenticationPrincipal String studentId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.registerDevice(
                studentId, "", studentId,
                body.get("fingerprint"),
                body.get("userAgent")));
    }

    // ── Excuse Requests ───────────────────────────────────

    @PostMapping("/excuse-request")
    public ResponseEntity<ExcuseDto.Response> submitExcuseRequest(
            @AuthenticationPrincipal String studentId,
            @Valid @RequestBody ExcuseDto.CreateRequest request) {
        return ResponseEntity.status(201).body(excuseService.submitRequest(studentId, request));
    }

    @GetMapping("/excuse-request")
    public ResponseEntity<List<ExcuseDto.Response>> getMyExcuseRequests(
            @AuthenticationPrincipal String studentId) {
        return ResponseEntity.ok(excuseService.getStudentRequests(studentId));
    }
}
