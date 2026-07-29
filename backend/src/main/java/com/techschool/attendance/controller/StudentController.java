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
