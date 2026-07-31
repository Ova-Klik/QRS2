package com.techschool.attendance.controller;

import com.techschool.attendance.dto.*;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.model.User;
import com.techschool.attendance.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AuthService authService;
    private final CohortService cohortService;
    private final AttendanceService attendanceService;
    private final HolidayService holidayService;
    private final ExportService exportService;
    private final AuditService auditService;

    // ── Users ──────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserDto.UserResponse>> listUsers(
            @RequestParam(required = false) String role) {
        User.Role r = (role != null) ? User.Role.valueOf(role.toUpperCase()) : User.Role.STUDENT;
        return ResponseEntity.ok(userService.getUsersByRole(r));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto.UserResponse> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto.UserResponse> createUser(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody UserDto.CreateUserRequest request) {
        var admin = userService.getById(adminId);
        return ResponseEntity.status(201).body(
                userService.createUser(adminId, admin.getName(), "SUPER_ADMIN", request));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserDto.UserResponse> updateUser(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id,
            @RequestBody UserDto.UpdateUserRequest request) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(
                userService.updateUser(adminId, admin.getName(), "SUPER_ADMIN", id, request));
    }

    // ── Student Search & Pagination ───────────────────────

    @GetMapping("/students/search")
    public ResponseEntity<AnalyticsDto.PageResponse<UserDto.UserResponse>> searchStudents(
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String order) {
        return ResponseEntity.ok(userService.searchStudents(cohortId, q, page, size, sort, order));
    }

    @GetMapping("/cohorts/{id}/students")
    public ResponseEntity<List<UserDto.UserResponse>> cohortStudents(@PathVariable String id) {
        return ResponseEntity.ok(userService.getStudentsByCohort(id));
    }

    /** Paginated + searchable + sortable student list scoped to a cohort. */
    @GetMapping("/cohorts/{id}/students/page")
    public ResponseEntity<AnalyticsDto.PageResponse<UserDto.UserResponse>> cohortStudentsPage(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "name") String sort,
            @RequestParam(required = false, defaultValue = "asc") String order) {
        return ResponseEntity.ok(userService.searchStudents(id, q, page, size, sort, order));
    }

    // ── Password Reset ─────────────────────────────────────

    @PostMapping("/users/reset-password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        authService.adminResetPassword(adminId, request);
        return ResponseEntity.ok().build();
    }

    // ── Devices ────────────────────────────────────────────

    @PostMapping("/devices/register")
    public ResponseEntity<UserDto.UserResponse.DeviceInfo> registerDevice(
            @AuthenticationPrincipal String adminId,
            @RequestBody Map<String, String> body) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(userService.registerDevice(
                adminId, admin.getName(),
                body.get("studentId"),
                body.get("fingerprint"),
                body.get("userAgent")));
    }

    @PostMapping("/devices/unlock/{studentId}")
    public ResponseEntity<Void> unlockDevice(
            @AuthenticationPrincipal String adminId,
            @PathVariable String studentId) {
        var admin = userService.getById(adminId);
        userService.unlockDevice(adminId, admin.getName(), studentId);
        return ResponseEntity.ok().build();
    }

    // ── Cohorts ────────────────────────────────────────────

    @GetMapping("/cohorts")
    public ResponseEntity<List<CohortDto.CohortResponse>> listCohorts() {
        return ResponseEntity.ok(cohortService.getAllCohorts());
    }

    @PostMapping("/cohorts")
    public ResponseEntity<CohortDto.CohortResponse> createCohort(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody CohortDto.CreateCohortRequest request) {
        var admin = userService.getById(adminId);
        return ResponseEntity.status(201).body(
                cohortService.createCohort(adminId, admin.getName(), request));
    }

    @PatchMapping("/cohorts/{id}/toggle")
    public ResponseEntity<CohortDto.CohortResponse> toggleCohort(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(cohortService.toggleCohort(adminId, admin.getName(), id));
    }

    // ── Audit Logs ─────────────────────────────────────────

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getRecent());
    }

    // ── Network Settings ──────────────────────────────────

    @GetMapping("/settings/network")
    public ResponseEntity<Map<String, String>> getNetworkSettings() {
        return ResponseEntity.ok(userService.getNetworkSettings());
    }

    @PutMapping("/settings/network")
    public ResponseEntity<Map<String, String>> updateNetworkSettings(
            @AuthenticationPrincipal String adminId,
            @RequestBody Map<String, String> settings) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(userService.updateNetworkSettings(adminId, admin.getName(), settings));
    }

    // ── Analytics ──────────────────────────────────────────

    @GetMapping("/analytics/school")
    public ResponseEntity<DashboardDto.AdminStats> getSchoolStats(
            @RequestParam(required = false) String cohortId) {
        return ResponseEntity.ok(cohortService.buildAdminStats(cohortId));
    }

    @GetMapping("/analytics/calendar")
    public ResponseEntity<AnalyticsDto.CalendarMonth> getCalendarMonth(
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        java.time.YearMonth ym = (year == null || month == null)
                ? java.time.YearMonth.now() : java.time.YearMonth.of(year, month);
        return ResponseEntity.ok(attendanceService.buildCalendarMonth(cohortId, ym.getYear(), ym.getMonthValue()));
    }

    @GetMapping("/analytics/students/{studentId}")
    public ResponseEntity<AnalyticsDto.StudentAnalytics> getStudentAnalytics(@PathVariable String studentId) {
        return ResponseEntity.ok(attendanceService.buildStudentAnalytics(studentId));
    }

    /** Per-student attendance report export (Student, Cohort, Date, Status, Source, Device). */
    @GetMapping("/analytics/students/{studentId}/export")
    public ResponseEntity<byte[]> exportStudentAttendance(
            @PathVariable String studentId,
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
                    r.getStudentName() != null ? r.getStudentName() : "",
                    r.getCohortName() != null ? r.getCohortName() : "",
                    r.getDate() != null ? r.getDate().toString() : "",
                    r.getStatus() != null ? r.getStatus() : "",
                    r.isManual() ? "MANUAL" : "QR",
                    r.getDeviceUsed() != null ? r.getDeviceUsed() : ""
            ));
        }
        return exportService.export(
                List.of("Student Name", "Cohort", "Date", "Status", "Source", "Device"),
                table, format, "student_" + studentId + "_attendance");
    }

    /** Per-student summary export (Attendance %, Present, Absent, Late, Excused, Holiday, Days attended/missed, streaks, rating). */
    @GetMapping("/analytics/students/{studentId}/summary/export")
    public ResponseEntity<byte[]> exportStudentSummary(
            @PathVariable String studentId,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        AnalyticsDto.StudentAnalytics a = attendanceService.buildStudentSummaryExport(studentId);
        List<List<Object>> table = new java.util.ArrayList<>();
        table.add(List.of(
                a.getStudentName() != null ? a.getStudentName() : "",
                a.getCohortName() != null ? a.getCohortName() : "",
                String.format("%.1f", a.getAttendanceRate()) + "%",
                String.valueOf(a.getPresent()),
                String.valueOf(a.getAbsent()),
                String.valueOf(a.getLate()),
                String.valueOf(a.getExcused()),
                String.valueOf(a.getHoliday()),
                String.valueOf(a.getPresent() + a.getLate()),
                String.valueOf(a.getAbsent() + a.getExcused()),
                String.valueOf(a.getLongestAttendanceStreak()),
                String.valueOf(a.getLongestAbsenceStreak()),
                a.getRating() != null ? a.getRating() : ""
        ));
        return exportService.export(
                List.of("Student Name", "Cohort", "Attendance Rate", "Present", "Absent", "Late",
                        "Excused", "Holiday", "Days Attended", "Days Missed",
                        "Longest Attendance Streak", "Longest Absence Streak", "Rating"),
                table, format, "student_" + studentId + "_summary");
    }

    // ── Attendance Search & Export ────────────────────────

    @GetMapping("/attendance/search")
    public ResponseEntity<AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord>> searchAttendance(
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Integer lastNDays,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        return ResponseEntity.ok(attendanceService.searchByDate(cohortId, start, end, lastNDays, page, size));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam(required = false) String cohortId,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Integer lastNDays,
            @RequestParam(required = false, defaultValue = "csv") String format,
            @AuthenticationPrincipal String adminId) {
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
                    r.getCohortName() != null ? r.getCohortName() : "",
                    r.getDate() != null ? r.getDate().toString() : "",
                    r.getStatus() != null ? r.getStatus() : "",
                    r.isManual() ? "MANUAL" : "QR",
                    r.getDeviceUsed() != null ? r.getDeviceUsed() : ""
            ));
        }

        ResponseEntity<byte[]> result = exportService.export(
                List.of("Student Name", "Student ID", "Cohort", "Date", "Status", "Source", "Device"),
                table, format, "attendance_" + effStart + "_to_" + effEnd);
        return result;
    }

    @GetMapping("/cohorts/{cohortId}/export")
    public ResponseEntity<byte[]> exportCohort(
            @PathVariable String cohortId,
            @RequestParam(required = false, defaultValue = "csv") String format,
            @AuthenticationPrincipal String adminId) {
        List<AnalyticsDto.CohortExportRow> rows = attendanceService.buildCohortExportRows(cohortId);
        List<List<Object>> table = new java.util.ArrayList<>();
        for (AnalyticsDto.CohortExportRow r : rows) {
            table.add(List.of(
                    r.getStudentName() != null ? r.getStudentName() : "",
                    r.getRegistrationNumber() != null ? r.getRegistrationNumber() : "",
                    String.format("%.1f", r.getAttendanceRate()) + "%",
                    String.valueOf(r.getPresent()),
                    String.valueOf(r.getLate()),
                    String.valueOf(r.getExcused()),
                    String.valueOf(r.getHoliday()),
                    String.valueOf(r.getDaysAttended()),
                    String.valueOf(r.getDaysMissed()),
                    String.valueOf(r.getSchoolDays())
            ));
        }

        return exportService.export(
                List.of("Student Name", "Registration No", "Attendance Rate",
                        "Present", "Late", "Excused", "Holidays", "Attended", "Absent", "School Days"),
                table, format, "cohort_" + cohortId + "_attendance");
    }

    // ── Holidays ───────────────────────────────────────────

    @GetMapping("/holidays")
    public ResponseEntity<List<HolidayDto.Response>> listHolidays() {
        return ResponseEntity.ok(holidayService.getAll());
    }

    @PostMapping("/holidays")
    public ResponseEntity<HolidayDto.Response> createHoliday(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody HolidayDto.CreateRequest request) {
        var admin = userService.getById(adminId);
        return ResponseEntity.status(201).body(
                holidayService.create(adminId, admin.getName(), request));
    }

    @PutMapping("/holidays/{id}")
    public ResponseEntity<HolidayDto.Response> updateHoliday(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id,
            @RequestBody HolidayDto.UpdateRequest request) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(holidayService.update(adminId, admin.getName(), id, request));
    }

    @PatchMapping("/holidays/{id}/toggle")
    public ResponseEntity<HolidayDto.Response> toggleHoliday(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id) {
        var admin = userService.getById(adminId);
        return ResponseEntity.ok(holidayService.toggle(adminId, admin.getName(), id));
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(
            @AuthenticationPrincipal String adminId,
            @PathVariable String id) {
        var admin = userService.getById(adminId);
        holidayService.delete(adminId, admin.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
