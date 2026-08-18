package com.techschool.attendance.service;

import com.techschool.attendance.dto.AuthDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.Cohort;
import com.techschool.attendance.model.User;
import com.techschool.attendance.repository.CohortRepository;
import com.techschool.attendance.repository.UserRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void testRegisterStudentTriggersVerificationEmail() {
        AuthDto.RegisterStudentRequest request = new AuthDto.RegisterStudentRequest();
        request.setName("Alice Smith");
        request.setEmail("alice@example.com");
        request.setPhone("+234 800 000 0000");
        request.setPassword("Password123");
        request.setCohortNumber("Cohort 29");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(cohortRepository.findByActive(true)).thenReturn(List.of(sampleCohort));
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("generated-user-id");
            return u;
        });

        AuthDto.LoginResponse response = authService.registerStudent(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("alice@example.com", response.getEmail());
        assertNull(response.getToken()); // Unverified registration returns null token

        // Verify MailService.sendVerificationEmail was called with correct recipient
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(mailService, times(1)).sendVerificationEmail(
                emailCaptor.capture(), nameCaptor.capture(), tokenCaptor.capture());

        assertEquals("alice@example.com", emailCaptor.getValue());
        assertEquals("Alice Smith", nameCaptor.getValue());
        assertNotNull(tokenCaptor.getValue());
        assertFalse(tokenCaptor.getValue().isBlank());
    }

    @Test
    void testLoginRejectsUnverifiedEmail() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
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

        AuthDto.MessageResponse response = authService.verifyEmail(validToken);

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
        AuthDto.ForgotPasswordRequest request = new AuthDto.ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        sampleStudent.setEmailVerified(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleStudent));

        AuthDto.MessageResponse response = authService.forgotPassword(request);

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

        AuthDto.ResetPasswordWithTokenRequest request = new AuthDto.ResetPasswordWithTokenRequest();
        request.setToken(resetToken);
        request.setNewPassword("NewSecret123");

        AuthDto.MessageResponse response = authService.resetPasswordWithToken(request);

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

        AuthDto.ResetPasswordWithTokenRequest request = new AuthDto.ResetPasswordWithTokenRequest();
        request.setToken(usedToken);
        request.setNewPassword("NewSecret123");

        AppException ex = assertThrows(AppException.class, () -> authService.resetPasswordWithToken(request));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }
}
