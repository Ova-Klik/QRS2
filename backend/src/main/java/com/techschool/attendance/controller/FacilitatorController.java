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

    // ── QR ────────────────────────────────────────────────

    @PostMapping("/qr/generate")
    public ResponseEntity<QrDto.QrResponse> generateQr(
            @AuthenticationPrincipal String facId,
            @Valid @RequestBody QrDto.GenerateRequest request) throws Exception {
        var fac = userService.getById(facId);
        return ResponseEntity.ok(
                qrService.generateSession(facId, fac.getName(), request.getCohortId(), request.getDurationMinutes()));
    }

    @GetMapping("/qr/active/{cohortId}")
    public ResponseEntity<QrDto.QrResponse> getActiveQr(
            @PathVariable String cohortId) throws Exception {
        return ResponseEntity.ok(qrService.getActiveSession(cohortId));
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

    // ── Dashboard ─────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto.FacilitatorStats> dashboard(
            @AuthenticationPrincipal String facId) throws Exception {
        return ResponseEntity.ok(cohortService.buildFacilitatorStats(facId));
    }
}
