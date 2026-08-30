package com.techschool.attendance.service;

import com.techschool.attendance.data.model.NetworkSettings;
import com.techschool.attendance.data.repository.NetworkSettingsRepository;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttendanceServiceCidrTest {

    @Mock
    private NetworkSettingsRepository networkSettingsRepository;

    @Mock
    private NetworkSettingsService networkSettingsService;

    @InjectMocks
    private AttendanceService attendanceService;

    private NetworkSettings createSettings(String ipRange) {
        return NetworkSettings.builder()
                .id("default")
                .schoolWifiSsids(List.of("Campus-WiFi"))
                .schoolIpRange(ipRange)
                .enforceNetwork(true)
                .enforceGeolocation(false)
                .build();
    }

    private QrRequestDto.ScanRequest createRequest(String clientIp) {
        QrRequestDto.ScanRequest req = new QrRequestDto.ScanRequest();
        req.setNetworkSSID("Campus-WiFi");
        req.setClientIP(clientIp);
        return req;
    }

    @Test
    @DisplayName("Regression Fix #1: /24 subnet rejects 192.168.100.5 and 192.168.19.1, accepts 192.168.1.42")
    void testCidr24_RegressionBugFix() {
        NetworkSettings settings = createSettings("192.168.1.0/24");
        when(networkSettingsService.getSettingsEntity()).thenReturn(settings);

        // Valid IP inside /24 range -> PASS
        String resultPass = attendanceService.verifyCheckIn(createRequest("192.168.1.42"), "student1", "192.168.1.42");
        assertEquals("WIFI", resultPass);

        // IPs starting with '192.168.1' prefix that previously bypassed naive string matching -> REJECT
        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("192.168.100.5"), "student1", "192.168.100.5"));

        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("192.168.19.1"), "student1", "192.168.19.1"));
    }

    @Test
    @DisplayName("Fix #1: /16 CIDR subnet matching generalizes correctly")
    void testCidr16_GeneralizesCorrectly() {
        NetworkSettings settings = createSettings("10.1.0.0/16");
        when(networkSettingsService.getSettingsEntity()).thenReturn(settings);

        // Inside /16 range -> PASS
        String resultPass = attendanceService.verifyCheckIn(createRequest("10.1.250.55"), "student1", "10.1.250.55");
        assertEquals("WIFI", resultPass);

        // Outside /16 range -> REJECT
        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("10.2.0.1"), "student1", "10.2.0.1"));
    }

    @Test
    @DisplayName("Fix #1: /32 and range with no slash treated as exact single-IP match")
    void testExactSingleIpMatch() {
        // Test with range without slash
        NetworkSettings settingsExact = createSettings("192.168.1.50");
        when(networkSettingsService.getSettingsEntity()).thenReturn(settingsExact);

        assertEquals("WIFI", attendanceService.verifyCheckIn(createRequest("192.168.1.50"), "student1", "192.168.1.50"));
        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("192.168.1.51"), "student1", "192.168.1.51"));

        // Test with explicit /32
        NetworkSettings settings32 = createSettings("192.168.1.50/32");
        when(networkSettingsService.getSettingsEntity()).thenReturn(settings32);

        assertEquals("WIFI", attendanceService.verifyCheckIn(createRequest("192.168.1.50"), "student1", "192.168.1.50"));
        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("192.168.1.51"), "student1", "192.168.1.51"));
    }

    @Test
    @DisplayName("Fix #1: IPv4 / IPv6 length mismatch handled safely without exception")
    void testIpAddressLengthMismatch_ReturnsFalseSafely() {
        NetworkSettings settings = createSettings("192.168.1.0/24");
        when(networkSettingsService.getSettingsEntity()).thenReturn(settings);

        // IPv6 address against IPv4 subnet range -> REJECT (no exception thrown)
        assertThrows(AppException.class, () ->
                attendanceService.verifyCheckIn(createRequest("2001:0db8:85a3:0000:0000:8a2e:0370:7334"), "student1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
    }
}
