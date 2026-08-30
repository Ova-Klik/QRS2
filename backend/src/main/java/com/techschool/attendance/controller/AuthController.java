package com.techschool.attendance.controller;

import com.techschool.attendance.dto.request.AuthRequestDto;
import com.techschool.attendance.dto.response.AuthResponseDto;
import com.techschool.attendance.dto.request.CohortRequestDto;
import com.techschool.attendance.dto.response.CohortResponseDto;
import com.techschool.attendance.dto.request.UserRequestDto;
import com.techschool.attendance.dto.response.UserResponseDto;
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
    public ResponseEntity<AuthResponseDto.LoginResponse> login(
            @Valid @RequestBody AuthRequestDto.LoginRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
    }

    // ── Public Cohort List (for registration) ────────────

    @GetMapping("/cohorts")
    public ResponseEntity<java.util.List<CohortResponseDto.CohortResponse>> listActiveCohorts() {
        return ResponseEntity.ok(cohortService.getActiveCohorts());
    }

    // ── Self-Registration (Public) ──────────────────────

    @PostMapping("/register/student")
    public ResponseEntity<AuthResponseDto.LoginResponse> registerStudent(
            @Valid @RequestBody AuthRequestDto.RegisterStudentRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(201).body(
                authService.registerStudent(request, http.getRemoteAddr()));
    }

    @PostMapping("/register/facilitator")
    public ResponseEntity<AuthResponseDto.LoginResponse> registerFacilitator(
            @Valid @RequestBody AuthRequestDto.RegisterFacilitatorRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(201).body(
                authService.registerFacilitator(request, http.getRemoteAddr()));
    }

    // ── Email Verification & Password Reset (Public) ─────

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponseDto.MessageResponse> verifyEmailPost(
            @Valid @RequestBody AuthRequestDto.VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.getToken()));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponseDto.MessageResponse> verifyEmailGet(
            @RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthResponseDto.MessageResponse> resendVerification(
            @Valid @RequestBody AuthRequestDto.ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponseDto.MessageResponse> forgotPassword(
            @Valid @RequestBody AuthRequestDto.ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponseDto.MessageResponse> resetPassword(
            @Valid @RequestBody AuthRequestDto.ResetPasswordWithTokenRequest request) {
        return ResponseEntity.ok(authService.resetPasswordWithToken(request));
    }

    // ── WebAuthn Biometric ──────────────────────────────

    @PostMapping("/webauthn/challenge")
    public ResponseEntity<AuthResponseDto.ChallengeResponse> biometricChallenge(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(authService.generateBiometricChallenge(userId));
    }

    @PostMapping("/webauthn/register")
    public ResponseEntity<Void> registerBiometric(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthRequestDto.WebAuthnRegisterRequest request) {
        authService.registerBiometric(userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webauthn/verify")
    public ResponseEntity<Boolean> verifyBiometric(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthRequestDto.WebAuthnVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyBiometric(userId, request));
    }

    // ── Profile & Password ──────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AuthRequestDto.ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto.UserResponse> me(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }
}
