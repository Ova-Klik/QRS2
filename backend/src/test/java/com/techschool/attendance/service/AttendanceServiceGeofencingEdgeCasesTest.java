package com.techschool.attendance.service;

import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.Device;
import com.techschool.attendance.model.QrSession;
import com.techschool.attendance.model.SystemSetting;
import com.techschool.attendance.model.User;
import com.techschool.attendance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AttendanceServiceGeofencingEdgeCasesTest {

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
    private CohortRepository cohortRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private User testStudent;
    private QrSession testSession;
    private Device testDevice;

    // School Reference Coordinates: Lagos, Nigeria (6.5244 N, 3.3792 E)
    private static final double SCHOOL_LAT = 6.5244;
    private static final double SCHOOL_LNG = 3.3792;
    private static final double DEFAULT_RADIUS_METERS = 150.0;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(attendanceService, "timezone", "Africa/Lagos");
        ReflectionTestUtils.setField(attendanceService, "windowStartDefault", "00:00");
        ReflectionTestUtils.setField(attendanceService, "windowEndDefault", "23:59");
        ReflectionTestUtils.setField(attendanceService, "lateThreshold", "23:59");

        testStudent = new User();
        testStudent.setId("student-geo-001");
        testStudent.setName("Geofence Tester");
        testStudent.setCohortId("cohort-geo-1");
        testStudent.setRole(User.Role.STUDENT);

        testSession = new QrSession();
        testSession.setId("session-geo-1");
        testSession.setCohortId("cohort-geo-1");
        testSession.setToken("GEO_TOKEN_123");
        testSession.setScanCount(0);

        testDevice = new Device();
        testDevice.setId("device-geo-1");
        testDevice.setStudentId("student-geo-001");
        testDevice.setFingerprint("fp-geo-001");
        testDevice.setLocked(true);

        mockDefaults();
    }

    private void mockDefaults() {
        when(userRepository.findById("student-geo-001")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsByStudentIdAndDate(eq("student-geo-001"), any(LocalDate.class))).thenReturn(false);
        when(qrService.validateToken("GEO_TOKEN_123")).thenReturn(testSession);
        when(deviceRepository.findByStudentId("student-geo-001")).thenReturn(Optional.of(testDevice));
        when(systemSettingRepository.findByKey("school_latitude")).thenReturn(Optional.of(new SystemSetting(null, "school_latitude", String.valueOf(SCHOOL_LAT), null)));
        when(systemSettingRepository.findByKey("school_longitude")).thenReturn(Optional.of(new SystemSetting(null, "school_longitude", String.valueOf(SCHOOL_LNG), null)));
        when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", String.valueOf((int) DEFAULT_RADIUS_METERS), null)));
    }

    private QrDto.ScanRequest buildScanRequest(Double lat, Double lng, Double accuracy) {
        QrDto.ScanRequest req = new QrDto.ScanRequest();
        req.setToken("GEO_TOKEN_123");
        req.setDeviceFingerprint("fp-geo-001");
        req.setLatitude(lat);
        req.setLongitude(lng);
        req.setAccuracy(accuracy);
        return req;
    }

    @Nested
    @DisplayName("Geofence Enforcement Disabled Tests")
    class DisabledGeofenceTests {

        @Test
        @DisplayName("Should succeed without location when geofence_enforce=false and fallback=false")
        void testDisabledGeofence_MissingCoordinates_Succeeds() {
            when(systemSettingRepository.findByKey("geofence_enforce")).thenReturn(Optional.of(new SystemSetting(null, "geofence_enforce", "false", null)));
            when(systemSettingRepository.findByKey("geofence_fallback_enabled")).thenReturn(Optional.of(new SystemSetting(null, "geofence_fallback_enabled", "false", null)));

            QrDto.ScanRequest request = buildScanRequest(null, null, null);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");

            assertNotNull(response);
            assertTrue(response.isSuccess());
            verify(attendanceRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Should succeed even if coordinates are far outside when geofencing is disabled")
        void testDisabledGeofence_FarCoordinates_Succeeds() {
            when(systemSettingRepository.findByKey("geofence_enforce")).thenReturn(Optional.of(new SystemSetting(null, "geofence_enforce", "false", null)));
            when(systemSettingRepository.findByKey("geofence_fallback_enabled")).thenReturn(Optional.of(new SystemSetting(null, "geofence_fallback_enabled", "false", null)));

            // 500km away
            QrDto.ScanRequest request = buildScanRequest(10.0, 10.0, 5.0);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");

            assertTrue(response.isSuccess());
        }
    }

    @Nested
    @DisplayName("Geofence Enforcement Active Edge Cases")
    class EnabledGeofenceEdgeCases {

        @BeforeEach
        void enableGeofence() {
            when(systemSettingRepository.findByKey("geofence_enforce")).thenReturn(Optional.of(new SystemSetting(null, "geofence_enforce", "true", null)));
        }

        @Test
        @DisplayName("Edge Case 1: Both latitude and longitude null -> Throws 400 Bad Request")
        void testBothCoordinatesNull_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(null, null, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Location coordinates are required"));
        }

        @Test
        @DisplayName("Edge Case 2: Latitude provided but longitude is null -> Throws 400 Bad Request")
        void testLatitudeOnly_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, null, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Location coordinates are required"));
        }

        @Test
        @DisplayName("Edge Case 3: Longitude provided but latitude is null -> Throws 400 Bad Request")
        void testLongitudeOnly_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(null, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Location coordinates are required"));
        }

        @Test
        @DisplayName("Edge Case 4: Null Island Coordinates (0.0, 0.0) -> Throws 400 Invalid Coordinates")
        void testNullIslandCoordinates_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(0.0, 0.0, 5.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Invalid location coordinates received"));
        }

        @Test
        @DisplayName("Edge Case 5: Out of bounds positive Latitude (> 90.0) -> Throws 400 Invalid Coordinates")
        void testLatitudeAbove90_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(90.1, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Invalid location coordinates received"));
        }

        @Test
        @DisplayName("Edge Case 6: Out of bounds negative Latitude (< -90.0) -> Throws 400 Invalid Coordinates")
        void testLatitudeBelowMinus90_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(-90.1, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Invalid location coordinates received"));
        }

        @Test
        @DisplayName("Edge Case 7: Out of bounds positive Longitude (> 180.0) -> Throws 400 Invalid Coordinates")
        void testLongitudeAbove180_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, 180.1, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Invalid location coordinates received"));
        }

        @Test
        @DisplayName("Edge Case 8: Out of bounds negative Longitude (< -180.0) -> Throws 400 Invalid Coordinates")
        void testLongitudeBelowMinus180_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, -180.1, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Invalid location coordinates received"));
        }

        @Test
        @DisplayName("Edge Case 9: Accuracy value is extremely high (> maxThreshold 3000m) -> Throws 400 Poor Accuracy")
        void testExcessiveAccuracyUncertainty_ThrowsBadRequest() {
            // maxRadius = 150m, threshold = max(3000, 1500) = 3000m. accuracy = 3500m
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 3500.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Your location accuracy (3500m) is too low"));
        }

        @Test
        @DisplayName("Edge Case 10: Accuracy value is null but coordinates are valid -> Passes accuracy check and succeeds")
        void testNullAccuracy_ValidCoordinates_Succeeds() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, null);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("Edge Case 11: Negative accuracy value -> Passes accuracy threshold check and evaluates distance")
        void testNegativeAccuracy_SucceedsIfInsideGeofence() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, -5.0);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("Edge Case 12: Student exactly at center (0m distance) -> Succeeds")
        void testExactCenterCoordinates_Succeeds() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 5.0);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("Edge Case 13: Student ~100m away (well within 150m radius) -> Succeeds")
        void testInsideRadius_100MetersAway_Succeeds() {
            // ~100m north of center (6.5244 + ~0.0009 deg)
            double lat100m = SCHOOL_LAT + 0.0009;
            QrDto.ScanRequest request = buildScanRequest(lat100m, SCHOOL_LNG, 10.0);
            QrDto.ScanResponse response = attendanceService.scanQr("student-geo-001", request, "127.0.0.1");
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("Edge Case 14: Student ~160m away (outside 150m radius) -> Throws 403 Forbidden")
        void testOutsideRadius_160MetersAway_ThrowsForbidden() {
            // ~160m north of center (6.5244 + ~0.00144 deg)
            double lat160m = SCHOOL_LAT + 0.00144;
            QrDto.ScanRequest request = buildScanRequest(lat160m, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("outside the allowed attendance location"));
        }
    

    @Nested
    @DisplayName("Geofence Fallback Setting Behavior")
    class FallbackSettingTests {

        @Test
        @DisplayName("When geofence_enforce=false BUT geofence_fallback_enabled=true, location enforcement must still execute")
        void testFallbackEnabled_EnforcesLocation() {
            when(systemSettingRepository.findByKey("geofence_enforce")).thenReturn(Optional.of(new SystemSetting(null, "geofence_enforce", "false", null)));
            when(systemSettingRepository.findByKey("geofence_fallback_enabled")).thenReturn(Optional.of(new SystemSetting(null, "geofence_fallback_enabled", "true", null)));

            QrDto.ScanRequest request = buildScanRequest(null, null, null);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertTrue(ex.getMessage().contains("Location coordinates are required"));
        }
    }

    @Nested
    @DisplayName("System Settings Vulnerabilities & Edge Cases (What Breaks?)")
    class SystemSettingsVulnerabilities {

        @BeforeEach
        void enableGeofence() {
            when(systemSettingRepository.findByKey("geofence_enforce")).thenReturn(Optional.of(new SystemSetting(null, "geofence_enforce", "true", null)));
        }

        @Test
        @DisplayName("WHAT BREAKS 1: Malformed non-numeric school_latitude setting -> Throws controlled 400 error (not 500)")
        void testMalformedSchoolLatitude_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_latitude")).thenReturn(Optional.of(new SystemSetting(null, "school_latitude", "INVALID_LAT", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("misconfigured"));
        }

        @Test
        @DisplayName("WHAT BREAKS 2: Malformed non-numeric school_geofence_radius_meters setting -> Throws controlled 400 error")
        void testMalformedRadiusSetting_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", "NOT_A_NUMBER", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("misconfigured"));
        }

        @Test
        @DisplayName("WHAT BREAKS 3: Negative radius setting (-50m) -> Throws controlled error for invalid radius")
        void testNegativeRadiusSetting_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", "-50", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 5.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("radius") || ex.getMessage().contains("misconfigured"));
        }

        @Test
        @DisplayName("WHAT BREAKS 4: Zero radius setting (0m) -> Throws controlled error for invalid radius")
        void testZeroRadiusSetting_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", "0", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT + 0.00005, SCHOOL_LNG, 5.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("radius") || ex.getMessage().contains("misconfigured"));
        }

        @Test
        @DisplayName("Non-numeric radius (NaN string) -> Throws controlled error")
        void testNaNRadiusSetting_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", "NaN", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
        }

        @Test
        @DisplayName("Infinity radius setting -> Throws controlled error for invalid radius")
        void testInfinityRadiusSetting_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_geofence_radius_meters")).thenReturn(Optional.of(new SystemSetting(null, "school_geofence_radius_meters", "Infinity", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
        }

        @Test
        @DisplayName("Non-numeric latitude setting -> Throws controlled 400 error")
        void testNonNumericLatitude_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_latitude")).thenReturn(Optional.of(new SystemSetting(null, "school_latitude", "abc", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
        }

        @Test
        @DisplayName("Non-numeric longitude setting -> Throws controlled 400 error")
        void testNonNumericLongitude_ThrowsBadRequest() {
            when(systemSettingRepository.findByKey("school_longitude")).thenReturn(Optional.of(new SystemSetting(null, "school_longitude", "not_a_number", null)));

            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
        }

        @Test
        @DisplayName("NaN latitude from GPS -> Throws 400 Invalid Coordinates")
        void testNaNGPSLatitude_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(Double.NaN, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("Invalid location coordinates"));
        }

        @Test
        @DisplayName("NaN longitude from GPS -> Throws 400 Invalid Coordinates")
        void testNaNGPSLongitude_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, Double.NaN, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("Invalid location coordinates"));
        }

        @Test
        @DisplayName("Infinite latitude from GPS -> Throws 400 Invalid Coordinates")
        void testInfiniteGPSLatitude_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(Double.POSITIVE_INFINITY, SCHOOL_LNG, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("Invalid location coordinates"));
        }

        @Test
        @DisplayName("Infinite longitude from GPS -> Throws 400 Invalid Coordinates")
        void testInfiniteGPSLongitude_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, Double.NEGATIVE_INFINITY, 10.0);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("Invalid location coordinates"));
        }

        @Test
        @DisplayName("NaN accuracy from GPS -> Throws 400 Invalid Accuracy")
        void testNaNAccuracy_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, Double.NaN);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("accuracy"));
        }

        @Test
        @DisplayName("Infinity accuracy from GPS -> Throws 400 Invalid Accuracy")
        void testInfinityAccuracy_ThrowsBadRequest() {
            QrDto.ScanRequest request = buildScanRequest(SCHOOL_LAT, SCHOOL_LNG, Double.POSITIVE_INFINITY);
            AppException ex = assertThrows(AppException.class, () -> attendanceService.scanQr("student-geo-001", request, "127.0.0.1"));
            assertEquals(400, ex.getStatus().value());
            assertTrue(ex.getMessage().contains("accuracy"));
        }
    }
    }}
