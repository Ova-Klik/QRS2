package com.techschool.attendance.service;

import com.techschool.attendance.dto.request.AuthRequestDto;
import com.techschool.attendance.dto.response.AuthResponseDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.security.JwtUtils;
import com.techschool.attendance.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceMailTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuditService auditService;

    @Mock
    private MailService mailService;

    @InjectMocks
    private AuthService authService;

    private User sampleStudent;
    private Cohort sampleCohort;

    @BeforeEach
    void setUp() {
        sampleCohort = new Cohort();
        sampleCohort.setId("cohort-29-id");
        sampleCohort.setName("Cohort 29");
        sampleCohort.setActive(true);

        sampleStudent = new User();
        sampleStudent.setId("user-1");
        sampleStudent.setName("Alice Smith");
        sampleStudent.setEmail("alice@example.com");
        sampleStudent.setPasswordHash("encoded_pass");
        sampleStudent.setRole(User.Role.STUDENT);
        sampleStudent.setActive(true);
        sampleStudent.setEmailVerified(false);
    }

    @Test
    void testRegisterStudentCreatesVerifiedAccountWithoutEmailDependency() {
        AuthRequestDto.RegisterStudentRequest request = new AuthRequestDto.RegisterStudentRequest();
        request.setName("Alice Smith");
        request.setEmail("alice@example.com");
        request.setPhone("+234 800 000 0000");
        request.setPassword("Password123");
        request.setCohortId("cohort-29-id");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(cohortRepository.findById("cohort-29-id")).thenReturn(Optional.of(sampleCohort));
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_pass");
        when(jwtUtils.generateToken("generated-user-id", "alice@example.com", "STUDENT"))
            .thenReturn("jwt-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("generated-user-id");
            return u;
        });

        AuthResponseDto.LoginResponse response = authService.registerStudent(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("generated-user-id", response.getUserId());
        assertEquals("jwt-token", response.getToken());
        verifyNoInteractions(mailService);
    }

    @Test
    void testLoginRejectsUnverifiedEmail() {
        AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Password123");

        sampleStudent.setEmailVerified(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Email is not verified"));
    }

    @Test
    void testVerifyEmailSuccess() {
        String validToken = "valid-token-123";
        sampleStudent.setVerificationToken(validToken);
        sampleStudent.setVerificationTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        sampleStudent.setEmailVerified(false);

        when(userRepository.findByVerificationToken(validToken)).thenReturn(Optional.of(sampleStudent));

        AuthResponseDto.MessageResponse response = authService.verifyEmail(validToken);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("verified successfully"));
        assertTrue(sampleStudent.isEmailVerified());
        assertNull(sampleStudent.getVerificationToken());
        assertNull(sampleStudent.getVerificationTokenExpiry());
        verify(userRepository, times(1)).save(sampleStudent);
    }

    @Test
    void testVerifyEmailExpiredTokenFails() {
        String expiredToken = "expired-token-123";
        sampleStudent.setVerificationToken(expiredToken);
        sampleStudent.setVerificationTokenExpiry(Instant.now().minus(1, ChronoUnit.HOURS));

        when(userRepository.findByVerificationToken(expiredToken)).thenReturn(Optional.of(sampleStudent));

        AppException ex = assertThrows(AppException.class, () -> authService.verifyEmail(expiredToken));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void testForgotPasswordTriggersResetEmail() {
        AuthRequestDto.ForgotPasswordRequest request = new AuthRequestDto.ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        sampleStudent.setEmailVerified(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));

        AuthResponseDto.MessageResponse response = authService.forgotPassword(request);

        assertNotNull(response);
        verify(userRepository, times(1)).save(sampleStudent);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(mailService, times(1)).sendPasswordResetEmail(
                emailCaptor.capture(), nameCaptor.capture(), tokenCaptor.capture());

        assertEquals("alice@example.com", emailCaptor.getValue());
        assertEquals("Alice Smith", nameCaptor.getValue());
        assertNotNull(tokenCaptor.getValue());
    }

    @Test
    void testResetPasswordWithTokenSuccess() {
        String resetToken = "reset-token-789";
        sampleStudent.setPasswordResetToken(resetToken);
        sampleStudent.setPasswordResetTokenExpiry(Instant.now().plus(30, ChronoUnit.MINUTES));

        when(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(sampleStudent));
        when(passwordEncoder.encode("NewSecret123")).thenReturn("encoded_new_secret");

        AuthRequestDto.ResetPasswordWithTokenRequest request = new AuthRequestDto.ResetPasswordWithTokenRequest();
        request.setToken(resetToken);
        request.setNewPassword("NewSecret123");

        AuthResponseDto.MessageResponse response = authService.resetPasswordWithToken(request);

        assertNotNull(response);
        assertTrue(response.getMessage().contains("reset successfully"));
        assertEquals("encoded_new_secret", sampleStudent.getPasswordHash());
        assertNull(sampleStudent.getPasswordResetToken());
        assertNull(sampleStudent.getPasswordResetTokenExpiry());
        verify(userRepository, times(1)).save(sampleStudent);
    }

    @Test
    void testResetPasswordWithTokenReuseFails() {
        String usedToken = "already-used-token";
        when(userRepository.findByPasswordResetToken(usedToken)).thenReturn(Optional.empty());

        AuthRequestDto.ResetPasswordWithTokenRequest request = new AuthRequestDto.ResetPasswordWithTokenRequest();
        request.setToken(usedToken);
        request.setNewPassword("NewSecret123");

        AppException ex = assertThrows(AppException.class, () -> authService.resetPasswordWithToken(request));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    // ── Login Tests ─────────────────────────────────────

    @Test
    void testSuccessfulLogin_validCredentials_returnsTokenAndUserInfo() {
        User admin = new User();
        admin.setId("admin-123");
        admin.setName("Super Admin");
        admin.setEmail("admin@techschool.edu");
        admin.setPasswordHash("hashed_admin_pass");
        admin.setRole(User.Role.SUPER_ADMIN);
        admin.setActive(true);
        admin.setEmailVerified(true);

        when(userRepository.findByEmail("admin@techschool.edu")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Admin@1234", "hashed_admin_pass")).thenReturn(true);
        when(jwtUtils.generateToken("admin-123", "admin@techschool.edu", "SUPER_ADMIN")).thenReturn("mocked-jwt-token-xyz");

        AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest();
        request.setEmail("admin@techschool.edu");
        request.setPassword("Admin@1234");

        AuthResponseDto.LoginResponse response = authService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("mocked-jwt-token-xyz", response.getToken());
        assertEquals("admin-123", response.getUserId());
        assertEquals("Super Admin", response.getName());
        assertEquals("admin@techschool.edu", response.getEmail());
        assertEquals("SUPER_ADMIN", response.getRole());
    }

    @Test
    void testLogin_invalidPassword_throwsUnauthorized() {
        sampleStudent.setEmailVerified(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));
        when(passwordEncoder.matches("WrongPass", "encoded_pass")).thenReturn(false);

        AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("WrongPass");

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Invalid email or password"));
    }

    @Test
    void testLogin_unverifiedEmail_throwsUnauthorized() {
        sampleStudent.setEmailVerified(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));

        AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Password123");

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Email is not verified"));
    }

    @Test
    void testLogin_deactivatedAccount_throwsUnauthorized() {
        sampleStudent.setActive(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));

        AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Password123");

        AppException ex = assertThrows(AppException.class, () -> authService.login(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Account is deactivated"));
    }
}
