package com.techschool.attendance.service;

import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.data.model.Device;
import com.techschool.attendance.data.model.NetworkSettings;
import com.techschool.attendance.data.model.QrSession;
import com.techschool.attendance.data.model.SystemSetting;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.data.repository.SystemSettingRepository;
import com.techschool.attendance.data.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceLocationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private QrService qrService;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HolidayService holidayService;
    @Mock
    private com.techschool.attendance.data.repository.CohortRepository cohortRepository;

    @Mock
    private NetworkSettingsService networkSettingsService;

    private AttendanceScanService attendanceService;

    private User testStudent;
    private QrSession testSession;
    private Device testDevice;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceScanService(
            attendanceRepository, userRepository, cohortRepository,
            systemSettingRepository, qrService, auditService, holidayService,
            new AttendanceCheckInService(networkSettingsService),
            new AttendanceDeviceService(deviceRepository));
        ReflectionTestUtils.setField(attendanceService, "timezone", "Africa/Lagos");
        ReflectionTestUtils.setField(attendanceService, "windowStartDefault", "00:00");
        ReflectionTestUtils.setField(attendanceService, "windowEndDefault", "23:59");
        ReflectionTestUtils.setField(attendanceService, "lateThreshold", "09:00");

        testStudent = new User();
        testStudent.setId("student-123");
        testStudent.setName("John Student");
        testStudent.setCohortId("cohort-1");
        testStudent.setRole(User.Role.STUDENT);

        testSession = new QrSession();
        testSession.setId("session-1");
        testSession.setCohortId("cohort-1");
        testSession.setToken("VALID123");
        testSession.setScanCount(0);

        testDevice = new Device();
        testDevice.setId("device-1");
        testDevice.setStudentId("student-123");
        testDevice.setFingerprint("fp-123");
        testDevice.setLocked(true);
    }

    private void mockCommonSuccess() {
        when(userRepository.findById("student-123")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsByStudentIdAndDate(eq("student-123"), any(LocalDate.class))).thenReturn(false);
        when(qrService.validateToken("VALID123")).thenReturn(testSession);
        when(deviceRepository.findByStudentId("student-123")).thenReturn(Optional.of(testDevice));
    }

    private NetworkSettings createSettings(boolean enforceGeo) {
        return NetworkSettings.builder()
                .id("default")
                .enforceNetwork(false)
                .enforceGeolocation(enforceGeo)
                .schoolLatitude(6.5244)
                .schoolLongitude(3.3792)
                .allowedRadiusMeters(150.0)
                .build();
    }

    @Test
    void testScanWithGeofenceEnforced_InsideGeofence_Succeeds() {
        mockCommonSuccess();
        when(networkSettingsService.getSettingsEntity()).thenReturn(createSettings(true));

        QrRequestDto.ScanRequest request = new QrRequestDto.ScanRequest();
        request.setToken("VALID123");
        request.setDeviceFingerprint("fp-123");
        request.setLatitude(6.5244);
        request.setLongitude(3.3792);
        request.setAccuracy(10.0);

        QrResponseDto.ScanResponse response = attendanceService.scanQr("student-123", request, "127.0.0.1");

        assertTrue(response.isSuccess());
        verify(attendanceRepository, times(1)).save(any());
    }

    @Test
    void testScanWithGeofenceEnforced_OutsideGeofence_ThrowsForbiddenWithDistance() {
        mockCommonSuccess();
        when(networkSettingsService.getSettingsEntity()).thenReturn(createSettings(true));

        QrRequestDto.ScanRequest request = new QrRequestDto.ScanRequest();
        request.setToken("VALID123");
        request.setDeviceFingerprint("fp-123");
        // Far away coordinates (~100km away)
        request.setLatitude(7.5244);
        request.setLongitude(3.3792);
        request.setAccuracy(15.0);

        AppException ex = assertThrows(AppException.class, () ->
                attendanceService.scanQr("student-123", request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("outside the allowed attendance location"));
        assertFalse(ex.getMessage().contains("enable location services"));
    }

    @Test
    void testScanWithGeofenceEnforced_MissingCoordinates_ThrowsBadRequest() {
        mockCommonSuccess();
        when(networkSettingsService.getSettingsEntity()).thenReturn(createSettings(true));

        QrRequestDto.ScanRequest request = new QrRequestDto.ScanRequest();
        request.setToken("VALID123");
        request.setDeviceFingerprint("fp-123");
        request.setLatitude(null);
        request.setLongitude(null);

        AppException ex = assertThrows(AppException.class, () ->
                attendanceService.scanQr("student-123", request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Location coordinates are required"));
    }

    @Test
    void testScanWithGeofenceEnforced_InvalidCoordinates_ThrowsBadRequest() {
        mockCommonSuccess();
        when(networkSettingsService.getSettingsEntity()).thenReturn(createSettings(true));

        QrRequestDto.ScanRequest request = new QrRequestDto.ScanRequest();
        request.setToken("VALID123");
        request.setDeviceFingerprint("fp-123");
        request.setLatitude(0.0);
        request.setLongitude(0.0);

        AppException ex = assertThrows(AppException.class, () ->
                attendanceService.scanQr("student-123", request, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Invalid location coordinates"));
    }

    @Test
    void testScanWithGeofenceDisabled_NoLocationRequired() {
        mockCommonSuccess();
        when(networkSettingsService.getSettingsEntity()).thenReturn(createSettings(false));

        QrRequestDto.ScanRequest request = new QrRequestDto.ScanRequest();
        request.setToken("VALID123");
        request.setDeviceFingerprint("fp-123");
        request.setLatitude(null);
        request.setLongitude(null);

        QrResponseDto.ScanResponse response = attendanceService.scanQr("student-123", request, "127.0.0.1");

        assertTrue(response.isSuccess());
        verify(attendanceRepository, times(1)).save(any());
    }
}
