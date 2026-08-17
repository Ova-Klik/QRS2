package com.techschool.attendance.service;

import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.Cohort;
import com.techschool.attendance.model.QrSession;
import com.techschool.attendance.model.SystemSetting;
import com.techschool.attendance.repository.CohortRepository;
import com.techschool.attendance.repository.QrSessionRepository;
import com.techschool.attendance.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrServiceTest {

    @Mock
    private QrSessionRepository qrSessionRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private QrService qrService;

    private Cohort testCohort;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrService, "timezone", "Africa/Lagos");
        ReflectionTestUtils.setField(qrService, "windowStartDefault", "07:00");
        ReflectionTestUtils.setField(qrService, "windowEndDefault", "23:59");

        testCohort = new Cohort();
        testCohort.setId("cohort-1");
        testCohort.setName("Software Engineering Cohort 1");
        testCohort.setActive(true);

        when(cohortRepository.findById("cohort-1")).thenReturn(Optional.of(testCohort));
        when(systemSettingRepository.findByKey("qr_refresh_enabled")).thenReturn(Optional.of(new SystemSetting(null, "qr_refresh_enabled", "true", null)));
        when(systemSettingRepository.findByKey("qr_refresh_interval")).thenReturn(Optional.of(new SystemSetting(null, "qr_refresh_interval", "15", null)));
    }

    @Test
    void testGenerateSession_ReusesExistingSessionDocument() throws Exception {
        QrSession existingSession = new QrSession();
        existingSession.setId("existing-session-id");
        existingSession.setCohortId("cohort-1");
        existingSession.setToken("OLD_TOKEN");

        when(qrSessionRepository.findFirstByCohortId("cohort-1")).thenReturn(Optional.of(existingSession));
        when(qrSessionRepository.save(any(QrSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QrDto.QrResponse response = qrService.generateSession("fac-1", "Facilitator One", "cohort-1", 30);

        assertNotNull(response);
        assertEquals("existing-session-id", response.getSessionId());
        assertEquals("cohort-1", response.getCohortId());
        assertEquals(QrSession.SessionState.ACTIVE, response.getState());

        verify(qrSessionRepository, times(1)).save(existingSession);
    }

    @Test
    void testGenerateSession_CreatesNewIfNoExistingSession() throws Exception {
        when(qrSessionRepository.findFirstByCohortId("cohort-2")).thenReturn(Optional.empty());
        Cohort cohort2 = new Cohort();
        cohort2.setId("cohort-2");
        cohort2.setName("Cohort 2");
        cohort2.setActive(true);
        when(cohortRepository.findById("cohort-2")).thenReturn(Optional.of(cohort2));

        when(qrSessionRepository.save(any(QrSession.class))).thenAnswer(invocation -> {
            QrSession s = invocation.getArgument(0);
            s.setId("new-session-id");
            return s;
        });

        QrDto.QrResponse response = qrService.generateSession("fac-1", "Facilitator One", "cohort-2", 60);

        assertNotNull(response);
        assertEquals("new-session-id", response.getSessionId());
        verify(qrSessionRepository, times(1)).save(any(QrSession.class));
    }

    @Test
    void testValidateToken_DynamicRollingToken_Success() {
        String masterToken = "MASTER_TOKEN_12345";
        QrSession session = new QrSession();
        session.setId("session-1");
        session.setCohortId("cohort-1");
        session.setToken(masterToken);
        session.setState(QrSession.SessionState.ACTIVE);
        session.setActiveFrom(Instant.now().minusSeconds(60));
        session.setExpiresAt(Instant.now().plusSeconds(3600));

        when(qrSessionRepository.findByToken(masterToken)).thenReturn(Optional.of(session));

        String rollingPayload = qrService.generateRollingTokenPayload(masterToken);
        QrSession validated = qrService.validateToken(rollingPayload);

        assertNotNull(validated);
        assertEquals("session-1", validated.getId());
    }

    @Test
    void testValidateToken_ExpiredSession_ThrowsBadRequest() {
        String masterToken = "EXPIRED_MASTER_TOKEN";
        QrSession expiredSession = new QrSession();
        expiredSession.setId("session-expired");
        expiredSession.setCohortId("cohort-1");
        expiredSession.setToken(masterToken);
        expiredSession.setState(QrSession.SessionState.EXPIRED);
        expiredSession.setActiveFrom(Instant.now().minusSeconds(3600));
        expiredSession.setExpiresAt(Instant.now().minusSeconds(60));

        when(qrSessionRepository.findByToken(masterToken)).thenReturn(Optional.of(expiredSession));

        String rollingPayload = qrService.generateRollingTokenPayload(masterToken);

        AppException ex = assertThrows(AppException.class, () -> qrService.validateToken(rollingPayload));
        assertTrue(ex.getMessage().contains("expired") || ex.getMessage().contains("not active"));
    }

    @Test
    void testAutoExpireSessions_TriggersDeletion() {
        Instant now = Instant.now();
        QrSession expired1 = new QrSession();
        expired1.setId("expired-1");
        expired1.setState(QrSession.SessionState.ACTIVE);

        when(qrSessionRepository.findByStateAndExpiresAtBefore(eq(QrSession.SessionState.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(expired1));

        qrService.autoExpireSessions();

        verify(qrSessionRepository, times(1)).save(expired1);
        verify(qrSessionRepository, times(1)).deleteByExpiresAtBefore(any(Instant.class));
    }
}
