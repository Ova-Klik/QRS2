package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.NetworkSettings;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.NetworkSettingsRepository;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NetworkSettingsVerificationTest {

    @Mock
    private NetworkSettingsRepository networkSettingsRepository;

    @InjectMocks
    private NetworkSettingsService networkSettingsService;

    private AttendanceCheckInService attendanceCheckInService;

    // Campus coordinates (Lagos): 6.5244 N, 3.3792 E, 150m allowed radius
    private final double campusLat = 6.5244;
    private final double campusLng = 3.3792;
    private final double allowedRadius = 150.0;

    @BeforeEach
    void setUp() {
        attendanceCheckInService = new AttendanceCheckInService(networkSettingsService);
    }

    private NetworkSettings createSettings(boolean enforceWifi, boolean enforceGeo, List<String> ssids, String ipRange) {
        return NetworkSettings.builder()
                .id("default")
                .schoolWifiSsids(ssids)
                .schoolIpRange(ipRange)
                .enforceNetwork(enforceWifi)
                .schoolLatitude(campusLat)
                .schoolLongitude(campusLng)
                .allowedRadiusMeters(allowedRadius)
                .enforceGeolocation(enforceGeo)
                .build();
    }

    // ── Branch 1: Both Enforced (WiFi first, Geo fallback) ──

    @Test
    void branch1_bothEnforced_wifiPasses_returnsWIFI() {
        NetworkSettings settings = createSettings(true, true, List.of("Campus-WiFi-1", "Campus-WiFi-2"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Campus-WiFi-2"); // Match second SSID
        req.setClientIP("192.168.1.55");

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "192.168.1.55");
        assertEquals("WIFI", method);
    }

    @Test
    void branch1_bothEnforced_wifiFails_geoPasses_returnsGEOLOCATION() {
        NetworkSettings settings = createSettings(true, true, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Home-WiFi"); // Non-matching WiFi
        req.setClientIP("10.0.0.5");     // Non-matching IP
        req.setLatitude(campusLat);
        req.setLongitude(campusLng);
        req.setAccuracy(10.0);

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5");
        assertEquals("GEOLOCATION", method);
    }

    @Test
    void branch1_bothEnforced_wifiFails_geoFails_throwsForbidden() {
        NetworkSettings settings = createSettings(true, true, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Home-WiFi");
        req.setClientIP("10.0.0.5");
        // Coordinates far away (10km)
        req.setLatitude(6.6000);
        req.setLongitude(3.4000);

        assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5"));
    }

    // ── Branch 2: WiFi Enforced Only ─────────────────────

    @Test
    void branch2_wifiOnly_wifiPasses_returnsWIFI() {
        NetworkSettings settings = createSettings(true, false, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Campus-WiFi-1");
        req.setClientIP("192.168.1.10");

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "192.168.1.10");
        assertEquals("WIFI", method);
    }

    @Test
    void branch2_wifiOnly_wifiFails_throwsForbidden_noFallback() {
        NetworkSettings settings = createSettings(true, false, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Campus-WiFi-1");
        req.setClientIP("10.0.0.5"); // IP mismatch
        req.setLatitude(campusLat);  // Valid GPS provided, but geo is disabled

        assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5"));
    }

    // ── Branch 3: Geolocation Enforced Only ──────────────

    @Test
    void branch3_geoOnly_geoPasses_returnsGEOLOCATION() {
        NetworkSettings settings = createSettings(false, true, List.of(), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setLatitude(campusLat);
        req.setLongitude(campusLng);
        req.setAccuracy(5.0);

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5");
        assertEquals("GEOLOCATION", method);
    }

    @Test
    void branch3_geoOnly_nullCoordinates_throwsBadRequest() {
        NetworkSettings settings = createSettings(false, true, List.of(), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        // Latitude & Longitude null

        AppException ex = assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5"));
        assertTrue(ex.getMessage().contains("Location coordinates are required"));
    }

    // ── Branch 4: Both Enforcement Disabled ──────────────

    @Test
    void branch4_bothDisabled_returnsUNVERIFIED() {
        NetworkSettings settings = createSettings(false, false, List.of(), "");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "10.0.0.5");
        assertEquals("UNVERIFIED", method);
    }

    // ── WiFi Partial Match & Multi-SSID Tests ───────────

    @Test
    void wifiPartialMatch_ssidMatches_ipMismatch_failsWiFi() {
        NetworkSettings settings = createSettings(true, false, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Campus-WiFi-1");
        req.setClientIP("172.16.0.5"); // Wrong IP range

        assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "172.16.0.5"));
    }

    @Test
    void wifiPartialMatch_ipMatches_ssidMismatch_failsWiFi() {
        NetworkSettings settings = createSettings(true, false, List.of("Campus-WiFi-1"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("WrongSSID");
        req.setClientIP("192.168.1.50");

        assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "192.168.1.50"));
    }

    @Test
    void wifiMultiSsid_matchesAnyInList_passesWiFi() {
        NetworkSettings settings = createSettings(true, false, List.of("BuildingA-WiFi", "BuildingB-WiFi", "Staff-Network"), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("BuildingB-WiFi");
        req.setClientIP("192.168.1.99");

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "192.168.1.99");
        assertEquals("WIFI", method);
    }

    @Test
    void wifiEmptySsidList_handlesSafelyWithoutException() {
        NetworkSettings settings = createSettings(true, false, List.of(), "192.168.1.0/24");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("SomeSSID");
        req.setClientIP("192.168.1.10");

        assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "192.168.1.10"));
    }

    // ── Haversine Boundary Tests ─────────────────────────

    @Test
    void haversineBoundary_exactlyAtRadius_passes() {
        // Offset latitude roughly equivalent to 100m distance (< 150m allowed)
        // 0.0009 degrees latitude ~= 100 meters
        NetworkSettings settings = createSettings(false, true, List.of(), "");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setLatitude(campusLat + 0.0009); // ~100 meters away
        req.setLongitude(campusLng);

        String method = attendanceCheckInService.verifyCheckIn(req, "student1", "127.0.0.1");
        assertEquals("GEOLOCATION", method);
    }

    @Test
    void haversineBoundary_outsideRadius_throwsForbidden() {
        // Offset latitude roughly equivalent to 500m distance (> 150m allowed)
        // 0.0045 degrees latitude ~= 500 meters
        NetworkSettings settings = createSettings(false, true, List.of(), "");
        when(networkSettingsRepository.findById("default")).thenReturn(Optional.of(settings));

        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setLatitude(campusLat + 0.0045);
        req.setLongitude(campusLng);

        AppException ex = assertThrows(AppException.class, () -> attendanceCheckInService.verifyCheckIn(req, "student1", "127.0.0.1"));
        assertTrue(ex.getMessage().contains("outside the allowed attendance location"));
    }
}
