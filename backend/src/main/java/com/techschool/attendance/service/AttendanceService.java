package com.techschool.attendance.service;

import com.techschool.attendance.dto.AuthDto;
import com.techschool.attendance.dto.AttendanceDto;
import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final DeviceRepository deviceRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final QrService qrService;
    private final AuditService auditService;
    private final AuthService authService;

    @Value("${app.attendance.late-threshold}")
    private String lateThreshold;

    @Value("${app.attendance.timezone}")
    private String timezone;

    @Value("${app.network.school-wifi-ssid:TechSchool-WiFi}")
    private String schoolWifiSsid;

    @Value("${app.network.school-ip-range:192.168.1.0/24}")
    private String schoolIpRange;

    @Value("${app.network.enforce:false}")
    private boolean enforceNetwork;

    // ── QR Scan ──────────────────────────────────────────
    public QrDto.ScanResponse scanQr(String studentId, QrDto.ScanRequest request, String ipAddress) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        // 1. Duplicate check
        if (attendanceRepository.existsByStudentIdAndDate(studentId, today)) {
            throw AppException.conflict("Attendance already marked for today");
        }

        // 2. Validate QR token
        QrSession session = qrService.validateToken(request.getToken());

        // 3. Cohort match
        if (!session.getCohortId().equals(student.getCohortId())) {
            throw AppException.forbidden("This QR code is not for your cohort");
        }

        // 4. School network validation
        validateSchoolNetwork(request, studentId);

        // 5. Device validation & first-scan auto-registration
        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        String incomingFingerprint = request.getDeviceFingerprint();
        if (incomingFingerprint == null || incomingFingerprint.trim().isEmpty()) {
            incomingFingerprint = "fp_" + (studentId.length() >= 8 ? studentId.substring(0, 8) : studentId);
        }

        if (device == null || !device.isLocked() || device.getFingerprint() == null) {
            if (device == null) {
                device = new Device();
                device.setStudentId(studentId);
            }
            device.setFingerprint(incomingFingerprint);
            device.setUserAgent(request.getUserAgent());
            device.setLocked(true);
            device.setRegisteredAt(Instant.now());
            device.setRegisteredBy("AUTO_FIRST_SCAN");
            device = deviceRepository.save(device);
            log.info("Auto-registered first device scan for student {}: {}", studentId, incomingFingerprint);
        } else {
            if (!incomingFingerprint.equals(device.getFingerprint())) {
                log.warn("Device fingerprint mismatch for student {} — expected {} got {}",
                        studentId, device.getFingerprint(), incomingFingerprint);
                throw AppException.forbidden(
                        "Device mismatch. Attendance can only be marked from your registered device. Contact your admin to reset your device.");
            }
        }

        // 6. Biometric verification (required if student has registered biometric)
        validateBiometric(student, request);

        // 7. Determine status
        Attendance.AttendanceStatus status = determineStatus();

        // 8. Save attendance
        Attendance attendance = new Attendance();
        attendance.setStudentId(studentId);
        attendance.setCohortId(student.getCohortId());
        attendance.setSessionId(session.getId());
        attendance.setDate(today);
        attendance.setMarkedAt(Instant.now());
        attendance.setStatus(status);
        attendance.setManual(false);
        attendance.setDeviceId(device.getId());
        attendance.setIpAddress(ipAddress);
        attendanceRepository.save(attendance);

        // 9. Increment scan count
        session.setScanCount(session.getScanCount() + 1);

        auditService.log(studentId, student.getName(), "STUDENT",
                AuditLog.ActionType.ATTENDANCE_MARKED,
                attendance.getId(), student.getName(),
                status + " — " + getCohortName(student.getCohortId()), ipAddress);

        return new QrDto.ScanResponse(true,
                "Attendance marked: " + status.name().toLowerCase(),
                status, attendance.getMarkedAt());
    }

    // ── Network & GPS Geofence Validation ────────────────
    private void validateSchoolNetwork(QrDto.ScanRequest request, String studentId) {
        String enforceVal = getSetting("network_enforce", "false");
        if (!Boolean.parseBoolean(enforceVal)) return;

        String schoolSsid = getSetting("school_wifi_ssid", schoolWifiSsid);
        String ipRange = getSetting("school_ip_range", schoolIpRange);

        boolean onSchoolNetwork = false;

        if (request.getNetworkSSID() != null && !request.getNetworkSSID().isEmpty()) {
            if (schoolSsid.equalsIgnoreCase(request.getNetworkSSID())) {
                onSchoolNetwork = true;
            }
        }

        if (!onSchoolNetwork && request.getClientIP() != null) {
            onSchoolNetwork = isIpInSchoolRange(request.getClientIP(), ipRange);
        }

        if (!onSchoolNetwork) {
            onSchoolNetwork = isIpInSchoolRange(studentId, ipRange);
        }

        if (onSchoolNetwork) return; // Passed via WiFi/IP network!

        // If network check fails, test GPS Geofence Fallback!
        boolean gpsFallbackEnabled = Boolean.parseBoolean(getSetting("geofence_fallback_enabled", "true"));
        if (gpsFallbackEnabled) {
            if (request.getLatitude() != null && request.getLongitude() != null) {
                double schoolLat = Double.parseDouble(getSetting("school_latitude", "6.5244"));
                double schoolLng = Double.parseDouble(getSetting("school_longitude", "3.3792"));
                double maxRadiusMeters = Double.parseDouble(getSetting("school_geofence_radius_meters", "150"));

                double distanceMeters = calculateHaversineDistanceMeters(
                        request.getLatitude(), request.getLongitude(), schoolLat, schoolLng);

                if (distanceMeters <= maxRadiusMeters) {
                    log.info("Student {} passed network check via GPS Geofence Fallback! Distance: {}m",
                            studentId, Math.round(distanceMeters));
                    return; // Passed via GPS Geofence!
                } else {
                    log.warn("Student {} failed GPS Geofence Fallback! Distance: {}m (max allowed: {}m)",
                            studentId, Math.round(distanceMeters), maxRadiusMeters);
                    throw AppException.forbidden(
                            "Network validation failed and your GPS location (" + Math.round(distanceMeters) +
                            "m away) is outside the school campus geofence perimeter (" + (int)maxRadiusMeters + "m max).");
                }
            } else {
                throw AppException.forbidden(
                        "School WiFi network disconnected. Please enable device GPS location services to mark attendance on campus.");
            }
        }

        log.warn("Student {} attempted attendance from non-school network. SSID={}, clientIP={}",
                studentId, request.getNetworkSSID(), request.getClientIP());
        throw AppException.forbidden(
                "You must be connected to the school WiFi network (" + schoolSsid + ") to mark attendance.");
    }

    private double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of earth in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isIpInSchoolRange(String ipOrStudentId, String ipRange) {
        try {
            String prefix = ipRange.substring(0, ipRange.lastIndexOf('.'));
            return ipOrStudentId.startsWith(prefix.replace("/", ""));
        } catch (Exception e) {
            return false;
        }
    }

    private String getSetting(String key, String defaultVal) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .orElse(defaultVal);
    }

    // ── Biometric Validation ─────────────────────────────
    private void validateBiometric(User student, QrDto.ScanRequest request) {
        // If student has a registered biometric credential, they MUST verify it
        if (student.getWebAuthnCredentialId() != null && !student.getWebAuthnCredentialId().isEmpty()) {
            if (!request.isBiometricVerified()) {
                throw AppException.forbidden(
                        "Biometric verification required. Please verify your fingerprint to mark attendance.");
            }

            if (request.getBiometricCredentialId() == null ||
                    !request.getBiometricCredentialId().equals(student.getWebAuthnCredentialId())) {
                throw AppException.forbidden("Biometric credential mismatch. Use the same device you registered.");
            }

            // Verify the biometric assertion server-side
            AuthDto.WebAuthnVerifyRequest verifyReq = new AuthDto.WebAuthnVerifyRequest();
            verifyReq.setCredentialId(request.getBiometricCredentialId());
            verifyReq.setAuthenticatorData(request.getBiometricAuthenticatorData());
            verifyReq.setClientDataJSON(request.getBiometricClientDataJSON());
            verifyReq.setSignature(request.getBiometricSignature());
            authService.verifyBiometric(student.getId(), verifyReq);
        }
    }

    // ── Manual Attendance ────────────────────────────────
    public AttendanceDto.AttendanceRecord markManual(String actorId, String actorName, String actorRole,
                                                      AttendanceDto.ManualMarkRequest request,
                                                      String ipAddress) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> AppException.notFound("Student not found"));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        // Upsert: create or update today's record
        Attendance attendance = attendanceRepository
                .findByStudentIdAndDate(request.getStudentId(), today)
                .orElse(new Attendance());

        attendance.setStudentId(request.getStudentId());
        attendance.setCohortId(student.getCohortId());
        attendance.setDate(today);
        attendance.setMarkedAt(Instant.now());
        attendance.setStatus(request.getStatus());
        attendance.setManual(true);
        attendance.setManualReason(request.getReason());
        attendance.setMarkedById(actorId);
        attendance.setIpAddress(ipAddress);
        Attendance saved = attendanceRepository.save(attendance);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.ATTENDANCE_MANUAL_OVERRIDE,
                student.getId(), student.getName(),
                "Manual " + request.getStatus() + " — " + request.getReason(), ipAddress);

        return toRecord(saved);
    }

    // ── Queries ──────────────────────────────────────────
    public List<AttendanceDto.AttendanceRecord> getStudentHistory(String studentId) {
        return attendanceRepository.findByStudentId(studentId)
                .stream().map(this::toRecord).collect(Collectors.toList());
    }

    public AttendanceDto.DailySummary getCohortSummaryToday(String cohortId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        return buildDailySummary(cohortId, today);
    }

    public AttendanceDto.DailySummary buildDailySummary(String cohortId, LocalDate date) {
        List<User> students = userRepository.findByCohortId(cohortId);
        List<Attendance> records = attendanceRepository.findByCohortIdAndDate(cohortId, date);
        Cohort cohort = cohortRepository.findById(cohortId).orElse(null);

        int present = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int manual = (int) records.stream().filter(Attendance::isManual).count();
        int total = students.size();
        int absent = total - records.size();
        double rate = total > 0 ? (double) (present + late) / total * 100 : 0;

        return new AttendanceDto.DailySummary(
                date, cohortId, cohort != null ? cohort.getName() : cohortId,
                total, present, late, absent, excused, manual, rate,
                records.stream().map(this::toRecord).collect(Collectors.toList())
        );
    }

    // ── Helpers ──────────────────────────────────────────
    private Attendance.AttendanceStatus determineStatus() {
        ZoneId zone = ZoneId.of(timezone);
        LocalTime now = LocalTime.now(zone);
        String thresholdVal = getSetting("late_threshold", lateThreshold);
        String[] parts = thresholdVal.split(":");
        LocalTime threshold = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        return now.isBefore(threshold) ? Attendance.AttendanceStatus.PRESENT : Attendance.AttendanceStatus.LATE;
    }

    private String getCohortName(String cohortId) {
        return cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId);
    }

    public AttendanceDto.AttendanceRecord toRecord(Attendance a) {
        User student = userRepository.findById(a.getStudentId()).orElse(null);
        Cohort cohort = a.getCohortId() != null ? cohortRepository.findById(a.getCohortId()).orElse(null) : null;
        return new AttendanceDto.AttendanceRecord(
                a.getId(), a.getStudentId(),
                student != null ? student.getName() : a.getStudentId(),
                a.getCohortId(), cohort != null ? cohort.getName() : a.getCohortId(),
                a.getDate(), a.getMarkedAt(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.isManual(), a.getManualReason()
        );
    }
}
