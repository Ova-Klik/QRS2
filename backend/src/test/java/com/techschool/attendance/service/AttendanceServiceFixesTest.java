package com.techschool.attendance.service;

import com.techschool.attendance.data.model.*;
import com.techschool.attendance.data.repository.*;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AttendanceServiceFixesTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CohortRepository cohortRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private QrService qrService;
    @Mock private AuditService auditService;
    @Mock private HolidayService holidayService;
    @Mock private ExcuseRequestRepository excuseRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NetworkSettingsService networkSettingsService;
    @Mock private AttendanceScanService attendanceScanService;
    @Mock private AttendanceCheckInService attendanceCheckInService;
    @Mock private AttendanceAnalyticsService attendanceAnalyticsService;
    @Mock private AttendanceFacilitatorReportService attendanceFacilitatorReportService;
    @Mock private AttendanceManualAttendanceService attendanceManualAttendanceService;
    @Mock private AttendanceExportService attendanceExportService;
    @Mock private AttendanceMarkingService attendanceMarkingService;

    @InjectMocks
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceAnalyticsService analyticsService;

    private User student;
    private QrSession session;
    private Device device;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(attendanceService, "timezone", "Africa/Lagos");
        ReflectionTestUtils.setField(analyticsService, "timezone", "Africa/Lagos");

        student = new User();
        student.setId("s100");
        student.setName("Jane Doe");
        student.setCohortId("c100");
        student.setRole(User.Role.STUDENT);

        session = new QrSession();
        session.setId("sess-100");
        session.setCohortId("c100");
        session.setToken("TOKEN-100");
        session.setScanCount(2);

        device = new Device();
        device.setId("d100");
        device.setStudentId("s100");
        device.setFingerprint("fp100");
        device.setLocked(true);

        when(userRepository.findById("s100")).thenReturn(Optional.of(student));
        when(attendanceRepository.existsByStudentIdAndDate(eq("s100"), any(LocalDate.class))).thenReturn(false);
        when(qrService.validateToken("TOKEN-100")).thenReturn(session);
        when(deviceRepository.findByStudentId("s100")).thenReturn(Optional.of(device));
        when(systemSettingRepository.findByKey("qr_window_start")).thenReturn(Optional.of(new SystemSetting(null, "qr_window_start", "00:00", null)));
        when(systemSettingRepository.findByKey("qr_window_end")).thenReturn(Optional.of(new SystemSetting(null, "qr_window_end", "23:59", null)));
        when(holidayService.isHoliday(any(LocalDate.class), eq("c100"))).thenReturn(false);
    }

    @Test
    @DisplayName("Fix #2: scanQr delegates to the dedicated attendance scan service")
    void testScanQr_DelegatesToScanService() {
        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setToken("TOKEN-100");
        req.setDeviceFingerprint("fp100");

        QrResponseDto.ScanResponse expected = new QrResponseDto.ScanResponse(true,
                "Attendance marked: present", Attendance.AttendanceStatus.PRESENT, Instant.now(), "UNVERIFIED");
        when(attendanceScanService.scanQr("s100", req, "127.0.0.1")).thenReturn(expected);

        QrResponseDto.ScanResponse resp = attendanceService.scanQr("s100", req, "127.0.0.1");

        assertEquals(expected, resp);
        verify(attendanceScanService, times(1)).scanQr("s100", req, "127.0.0.1");
    }

    @Test
    @DisplayName("Fix #3: buildStudentAnalytics creates StudentAnalytics with correct field mappings")
    void testBuildStudentAnalytics_FieldMapping() {
        when(userRepository.findById("s100")).thenReturn(Optional.of(student));
        Cohort cohort = new Cohort();
        cohort.setId("c100");
        cohort.setName("Software Engineering");
        when(cohortRepository.findById("c100")).thenReturn(Optional.of(cohort));
        when(attendanceRepository.findByStudentIdOrderByDateAsc("s100")).thenReturn(List.of());
        when(excuseRepository.findByStudentIdOrderByCreatedAtDesc("s100")).thenReturn(List.of());

        AnalyticsResponseDto.StudentAnalytics analytics = analyticsService.buildStudentAnalytics("s100");

        assertNotNull(analytics);
        assertEquals("s100", analytics.getStudentId());
        assertEquals("Jane Doe", analytics.getStudentName());
        assertEquals("c100", analytics.getCohortId());
        assertEquals("Software Engineering", analytics.getCohortName());
    }

    @Test
    @DisplayName("Fix #4: Unused private count method was removed from AttendanceService")
    void testDeadCodeCountMethod_Removed() {
        Method[] methods = AttendanceService.class.getDeclaredMethods();
        boolean foundCountMethod = Arrays.stream(methods)
                .anyMatch(m -> m.getName().equals("count") &&
                        m.getParameterCount() == 2 &&
                        m.getParameterTypes()[0].equals(List.class));

        assertFalse(foundCountMethod, "The dead code private count(List, AttendanceStatus) method should be removed");
    }

    @Test
    @DisplayName("Fix #6: exportPublicProjectionReport delegates to exportFacilitatorReport")
    void testExportPublicProjectionReport_DelegatesCorrectly() {
        ExportService mockExportService = mock(ExportService.class);
        org.springframework.http.ResponseEntity<byte[]> mockResp = org.springframework.http.ResponseEntity.ok(new byte[]{1, 2, 3});
        when(attendanceExportService.exportPublicProjectionReport("c100", LocalDate.now(), "xlsx", "127.0.0.1", mockExportService))
            .thenReturn(mockResp);

        org.springframework.http.ResponseEntity<byte[]> resp = attendanceService.exportPublicProjectionReport(
                "c100", LocalDate.now(), "xlsx", "127.0.0.1", mockExportService);

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCodeValue());
        verify(attendanceExportService).exportPublicProjectionReport(
            "c100", LocalDate.now(), "xlsx", "127.0.0.1", mockExportService);
    }
}
