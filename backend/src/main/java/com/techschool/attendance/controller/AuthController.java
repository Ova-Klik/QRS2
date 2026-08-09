package com.techschool.attendance.controller;

import com.techschool.attendance.dto.AuthDto;
import com.techschool.attendance.dto.CohortDto;
import com.techschool.attendance.dto.UserDto;
import com.techschool.attendance.service.AuthService;
import com.techschool.attendance.service.CohortService;
import com.techschool.attendance.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final CohortService cohortService;

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }

    // ── Public Cohort List (for registration) ────────────

    @GetMapping("/cohorts")
    public ResponseEntity<java.util.List<CohortDto.CohortResponse>> listActiveCohorts() {
        return ResponseEntity.ok(cohortService.getActiveCohorts());
    }

    // ── Self-Registration (Public) ──────────────────────

    @PostMapping("/register/student")
    public ResponseEntity<AuthDto.LoginResponse> registerStudent(
            @Valid @RequestBody AuthDto.RegisterStudentRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(201).body(
                authService.registerStudent(request, http.getRemoteAddr()));
    }

    @PostMapping("/register/facilitator")
    public ResponseEntity<AuthDto.LoginResponse> registerFacilitator(
            @Valid @RequestBody AuthDto.RegisterFacilitatorRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(201).body(
                authService.registerFacilitator(request, http.getRemoteAddr()));
    }

    // ── Email Verification & Password Reset (Public) ─────

    @PostMapping("/verify-email")
    public ResponseEntity<AuthDto.MessageResponse> verifyEmailPost(
            @Valid @RequestBody AuthDto.VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.getToken()));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthDto.MessageResponse> verifyEmailGet(
            @RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthDto.MessageResponse> resendVerification(
            @Valid @RequestBody AuthDto.ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthDto.MessageResponse> forgotPassword(
            @Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.MessageResponse> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordWithTokenRequest request) {
        return ResponseEntity.ok(authService.resetPasswordWithToken(request));
    }

    // ── WebAuthn Biometric ──────────────────────────────

    @PostMapping("/webauthn/challenge")
    public ResponseEntity<AuthDto.ChallengeResponse> biometricChallenge(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(authService.generateBiometricChallenge(userId));
    }

    @PostMapping("/webauthn/register")
    public ResponseEntity<Void> registerBiometric(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthDto.WebAuthnRegisterRequest request) {
        authService.registerBiometric(userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webauthn/verify")
    public ResponseEntity<Boolean> verifyBiometric(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthDto.WebAuthnVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyBiometric(userId, request));
    }

    // ── Profile & Password ──────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthDto.ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto.UserResponse> me(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }
}
