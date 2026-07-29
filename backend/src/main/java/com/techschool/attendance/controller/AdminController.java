package com.techschool.attendance.controller;

import com.techschool.attendance.dto.*;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.model.User;
import com.techschool.attendance.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<DashboardDto.AdminStats> getSchoolStats() {
        return ResponseEntity.ok(cohortService.buildAdminStats());
    }
}
