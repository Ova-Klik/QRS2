package com.techschool.attendance.service;

import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.request.AttendanceRequestDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.data.model.*;
import com.techschool.attendance.data.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
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
    private final HolidayService holidayService;
    private final ExcuseRequestRepository excuseRepository;
    private final AuditLogRepository auditLogRepository;
    private final NetworkSettingsService networkSettingsService;

    @Value("${app.attendance.late-threshold}")
    private String lateThreshold;

    @Value("${app.attendance.qr-window-start:07:00}")
    private String windowStartDefault;

    @Value("${app.attendance.qr-window-end:12:00}")
    private String windowEndDefault;

    @Value("${app.attendance.timezone}")
    private String timezone;

    // ── QR Scan ──────────────────────────────────────────
    public QrResponseDto.ScanResponse scanQr(String studentId, QrRequestDto.ScanRequest request, String ipAddress) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));

        ZonedDateTime nowZone = ZonedDateTime.now(ZoneId.of(timezone));
        java.time.DayOfWeek dayOfWeek = nowZone.getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            throw AppException.badRequest("Attendance recording is disabled on weekends (Saturday/Sunday).");
        }

        java.time.LocalTime currentTime = nowZone.toLocalTime();

        String windowStartStr = getSetting("qr_window_start", windowStartDefault);
        String windowEndStr = getSetting("qr_window_end", windowEndDefault);
        java.time.LocalTime startTime = java.time.LocalTime.parse(windowStartStr);
        java.time.LocalTime endTime = java.time.LocalTime.parse(windowEndStr);

        if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
            throw AppException.badRequest("Attendance is unavailable outside the permitted time window (" + windowStartStr + " – " + windowEndStr + " Mon-Fri).");
        }

        LocalDate today = nowZone.toLocalDate();

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

        // 4. School network & geofence validation
        String verificationMethod = verifyCheckIn(request, studentId, ipAddress);

        // 5. Device validation & first-scan auto-registration
        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        String incomingFingerprint = request.getDeviceFingerprint();
        if (incomingFingerprint == null || incomingFingerprint.trim().isEmpty()) {
            throw AppException.badRequest("Device fingerprint is required to mark attendance.");
        }
        incomingFingerprint = incomingFingerprint.trim();

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

        // Determine status (holidays are automatically recorded as HOLIDAY, never ABSENT)
        Attendance.AttendanceStatus status = determineStatus(student.getCohortId());

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
        attendance.setVerificationMethod(verificationMethod);
        attendanceRepository.save(attendance);

        // 9. Increment scan count
        qrService.incrementScanCount(session.getId());

        auditService.log(studentId, student.getName(), "STUDENT",
                AuditLog.ActionType.ATTENDANCE_MARKED,
                attendance.getId(), student.getName(),
                status + " (" + verificationMethod + ") — " + getCohortName(student.getCohortId()), ipAddress);

        return new QrResponseDto.ScanResponse(true,
                "Attendance marked: " + status.name().toLowerCase(),
                status, attendance.getMarkedAt(), verificationMethod);
    }

    // ── Network & GPS Geofence Verification Logic ────────
    public String verifyCheckIn(QrRequestDto.ScanRequest request, String studentId, String remoteIp) {
        NetworkSettings settings = networkSettingsService.getSettingsEntity();

        boolean enforceWifi = settings.isEnforceNetwork();
        boolean enforceGeo = settings.isEnforceGeolocation();

        // 1. Both enforced: Check WiFi first (SSID + IP required). If pass -> WIFI. Else fallback to Geolocation.
        if (enforceWifi && enforceGeo) {
            boolean wifiSuccess = isWifiVerified(request, remoteIp, settings);
            if (wifiSuccess) {
                log.info("Student {} verified check-in via WIFI.", studentId);
                return "WIFI";
            }
            log.info("Student {} failed Wi-Fi check; falling back to Geolocation check.", studentId);
            boolean geoSuccess = isGeolocationVerified(request, studentId, settings);
            if (geoSuccess) {
                log.info("Student {} verified check-in via GEOLOCATION (Wi-Fi fallback).", studentId);
                return "GEOLOCATION";
            }
            throw AppException.forbidden("Check-in failed: Unable to verify location via school Wi-Fi network or GPS geofence.");
        }

        // 2. Wi-Fi enforced, Geolocation disabled: Check WiFi only (no fallback).
        if (enforceWifi && !enforceGeo) {
            boolean wifiSuccess = isWifiVerified(request, remoteIp, settings);
            if (!wifiSuccess) {
                String ssids = (settings.getSchoolWifiSsids() != null && !settings.getSchoolWifiSsids().isEmpty()) 
                        ? String.join(", ", settings.getSchoolWifiSsids()) : "None configured";
                log.warn("Student {} failed Wi-Fi check (Wi-Fi only mode). SSID={}, clientIP={}",
                        studentId, request.getNetworkSSID(), request.getClientIP());
                throw AppException.forbidden("Attendance can only be marked while connected to an authorized school network (" + ssids + ").");
            }
            log.info("Student {} verified check-in via WIFI.", studentId);
            return "WIFI";
        }

        // 3. Geolocation enforced, Wi-Fi disabled: Check Geolocation only.
        if (!enforceWifi && enforceGeo) {
            boolean geoSuccess = isGeolocationVerified(request, studentId, settings);
            if (!geoSuccess) {
                throw AppException.forbidden("Attendance verification failed for geolocation.");
            }
            log.info("Student {} verified check-in via GEOLOCATION.", studentId);
            return "GEOLOCATION";
        }

        // 4. Both disabled: Free check-in.
        log.info("Student {} check-in unverified (network and geolocation enforcement disabled).", studentId);
        return "UNVERIFIED";
    }

    private boolean isWifiVerified(QrRequestDto.ScanRequest request, String remoteIp, NetworkSettings settings) {
        List<String> allowedSsids = settings.getSchoolWifiSsids();
        String ipRange = settings.getSchoolIpRange();

        // Must match ANY SSID in allowedSsids
        boolean ssidMatch = false;
        if (allowedSsids != null && !allowedSsids.isEmpty() && request.getNetworkSSID() != null && !request.getNetworkSSID().isBlank()) {
            String clientSsid = request.getNetworkSSID().trim();
            for (String allowed : allowedSsids) {
                if (allowed != null && allowed.equalsIgnoreCase(clientSsid)) {
                    ssidMatch = true;
                    break;
                }
            }
        }

        // Must match IP range
        boolean ipMatch = false;
        String clientIp = (request.getClientIP() != null && !request.getClientIP().isBlank())
                ? request.getClientIP().trim()
                : (remoteIp != null ? remoteIp.trim() : null);

        if (clientIp != null && ipRange != null && !ipRange.isBlank()) {
            ipMatch = isIpInSchoolRange(clientIp, ipRange);
        }

        // Both SSID match AND IP match required for WiFi verification
        return ssidMatch && ipMatch;
    }

    private boolean isGeolocationVerified(QrRequestDto.ScanRequest request, String studentId, NetworkSettings settings) {
        Double lat = request.getLatitude();
        Double lng = request.getLongitude();
        Double accuracy = request.getAccuracy();

        if (lat == null || lng == null) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"MISSING_COORDINATES\"}", studentId);
            throw AppException.badRequest("Location coordinates are required to mark attendance when geofencing is enabled.");
        }

        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0 || (lat == 0.0 && lng == 0.0)
                || Double.isNaN(lat) || Double.isNaN(lng) || Double.isInfinite(lat) || Double.isInfinite(lng)) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"INVALID_COORDINATES\"}", studentId);
            throw AppException.badRequest("Invalid location coordinates received. Please ensure your device has a valid GPS fix and try again.");
        }

        double schoolLat = settings.getSchoolLatitude();
        double schoolLng = settings.getSchoolLongitude();
        double allowedRadius = settings.getAllowedRadiusMeters();

        if (Double.isNaN(schoolLat) || Double.isInfinite(schoolLat) || schoolLat < -90.0 || schoolLat > 90.0 ||
            Double.isNaN(schoolLng) || Double.isInfinite(schoolLng) || schoolLng < -180.0 || schoolLng > 180.0) {
            log.warn("Geofence school location misconfigured: lat={}, lng={}", schoolLat, schoolLng);
            throw AppException.badRequest("Geofence school location is misconfigured. Please contact your administrator.");
        }

        if (Double.isNaN(allowedRadius) || Double.isInfinite(allowedRadius) || allowedRadius <= 0) {
            log.warn("Invalid geofence radius: {}. Must be > 0.", allowedRadius);
            throw AppException.badRequest("Geofence radius is misconfigured (must be > 0). Please contact your administrator.");
        }

        if (accuracy != null && !Double.isFinite(accuracy)) {
            throw AppException.badRequest("Invalid location accuracy value received. Please try again.");
        }

        if (accuracy != null && accuracy > Math.max(3000.0, allowedRadius * 10.0)) {
            throw AppException.badRequest("Your location accuracy (" + Math.round(accuracy) + "m) is too low. Please move to an open area with better GPS signal and try again.");
        }

        double distanceMeters = calculateHaversineDistanceMeters(lat, lng, schoolLat, schoolLng);

        if (!Double.isFinite(distanceMeters)) {
            throw AppException.badRequest("Unable to calculate distance from school. Please ensure your device has a valid GPS fix and try again.");
        }

        if (distanceMeters > allowedRadius) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"OUTSIDE_GEOFENCE\", \"distance\":{}, \"allowedRadius\":{}}",
                    studentId, Math.round(distanceMeters), allowedRadius);
            throw AppException.forbidden(
                    "You are outside the allowed attendance location (" +
                    Math.round(distanceMeters) + "m away, max allowed: " + (int)allowedRadius + "m).");
        }

        log.info("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"INSIDE_GEOFENCE\", \"distance\":{}, \"allowedRadius\":{}}",
                studentId, Math.round(distanceMeters), allowedRadius);
        return true;
    }

    private static final int EARTH_RADIUS_METERS = 6371000;

    private double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        if (!Double.isFinite(lat1) || !Double.isFinite(lon1) || !Double.isFinite(lat2) || !Double.isFinite(lon2)) {
            return Double.NaN;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        a = Math.max(0.0, Math.min(1.0, a));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_METERS * c;
        return Double.isFinite(distance) ? distance : Double.NaN;
    }

    private boolean isIpInSchoolRange(String clientIp, String ipRange) {
        if (clientIp == null || clientIp.isBlank() || ipRange == null || ipRange.isBlank()) return false;
        try {
            String cleanIp = clientIp.trim();
            String range = ipRange.trim();

            String subnetStr;
            int prefixLength;

            if (range.contains("/")) {
                String[] parts = range.split("/", 2);
                subnetStr = parts[0].trim();
                prefixLength = Integer.parseInt(parts[1].trim());
            } else {
                subnetStr = range;
                prefixLength = 32;
            }

            java.net.InetAddress ipAddr = java.net.InetAddress.getByName(cleanIp);
            java.net.InetAddress subnetAddr = java.net.InetAddress.getByName(subnetStr);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] subnetBytes = subnetAddr.getAddress();

            if (ipBytes.length != subnetBytes.length) return false;
            if (prefixLength < 0 || prefixLength > ipBytes.length * 8) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != subnetBytes[i]) return false;
            }

            if (remainingBits > 0 && fullBytes < ipBytes.length) {
                int mask = 0xFF << (8 - remainingBits) & 0xFF;
                if ((ipBytes[fullBytes] & mask) != (subnetBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to evaluate IP range match for ip={}, range={}: {}", clientIp, ipRange, e.getMessage());
            return false;
        }
    }

    private String getSetting(String key, String defaultVal) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .orElse(defaultVal);
    }

    // ── Manual Attendance ────────────────────────────────
    public AttendanceResponseDto.AttendanceRecord markManual(String actorId, String actorName, String actorRole,
                                                      AttendanceRequestDto.ManualMarkRequest request,
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
    public List<AttendanceResponseDto.AttendanceRecord> getStudentHistory(String studentId) {
        return buildRecords(attendanceRepository.findByStudentId(studentId));
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getStudentHistoryPage(
            String studentId, int page, int size) {
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));
        Page<Attendance> result = attendanceRepository.findByStudentId(studentId, pageable);
        return new AnalyticsResponseDto.PageResponse<>(buildRecords(result.getContent()),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    public AttendanceResponseDto.DailySummary getCohortSummaryToday(String cohortId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        return buildDailySummary(cohortId, today);
    }

    public AttendanceResponseDto.DailySummary buildDailySummary(String cohortId, LocalDate date) {
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        List<Attendance> records = attendanceRepository.findByCohortIdAndDate(cohortId, date);
        Cohort cohort = cohortRepository.findById(cohortId).orElse(null);
        String cohortName = cohort != null ? cohort.getName() : cohortId;

        int present = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int holidayMarked = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
        int manual = (int) records.stream().filter(Attendance::isManual).count();
        int total = students.size();

        boolean isHoliday = holidayService.isHoliday(date, cohortId);
        int holiday = isHoliday ? holidayMarked + Math.max(0, total - records.size()) : holidayMarked;
        int absent = isHoliday ? 0 : Math.max(0, total - records.size());
        double rate = total > 0 ? (double) (present + late) / total * 100 : 0;

        return new AttendanceResponseDto.DailySummary(
                date, cohortId, cohortName,
                total, present, late, absent, excused, holiday, manual, rate,
                buildRecords(records)
        );
    }

    // ── Calendar ─────────────────────────────────────────

    public AnalyticsResponseDto.CalendarMonth buildCalendarMonth(String cohortId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        String cohortName = "All Cohorts";
        List<User> students;
        if (cohortId != null && !cohortId.isBlank()) {
            Cohort cohort = cohortRepository.findById(cohortId).orElse(null);
            cohortName = cohort != null ? cohort.getName() : cohortId;
            students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        } else {
            students = userRepository.findByRole(User.Role.STUDENT);
        }

        List<Attendance> records = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, first, last)
                : attendanceRepository.findByDateBetween(first, last);

        Map<LocalDate, List<Attendance>> byDay = records.stream()
                .collect(Collectors.groupingBy(Attendance::getDate));
        int totalStudents = students.size();
        Map<LocalDate, String> holidays = holidayService.holidayNamesBetween(first, last,
                cohortId != null && !cohortId.isBlank() ? cohortId : null);

        List<AnalyticsResponseDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            boolean isHoliday = holidays.containsKey(d);
            String holidayName = holidays.get(d);

            List<Attendance> dayRecs = byDay.getOrDefault(d, List.of());
            int present = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int excused = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            int holidayCount = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
            int absentMarked = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int absent = (weekend || isHoliday)
                    ? 0
                    : Math.max(0, totalStudents - dayRecs.size()) + absentMarked;

            days.add(new AnalyticsResponseDto.CalendarDay(
                    d, weekend, isHoliday, holidayName,
                    present, late, absent, excused, holidayCount, totalStudents));
        }

        return new AnalyticsResponseDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    public AnalyticsResponseDto.CalendarMonth buildStudentCalendarMonth(String studentId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null
                ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";

        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, first, last);
        Map<LocalDate, Attendance> byDay = records.stream()
                .collect(Collectors.toMap(Attendance::getDate, Function.identity(), (a, b) -> a));
        Map<LocalDate, String> holidays = holidayService.holidayNamesBetween(first, last, cohortId);

        List<AnalyticsResponseDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            boolean isHoliday = holidays.containsKey(d);
            Attendance rec = byDay.get(d);
            Attendance.AttendanceStatus st = rec != null ? rec.getStatus() : null;

            int present = st == Attendance.AttendanceStatus.PRESENT ? 1 : 0;
            int late = st == Attendance.AttendanceStatus.LATE ? 1 : 0;
            int excused = st == Attendance.AttendanceStatus.EXCUSED ? 1 : 0;
            int holidayCount = st == Attendance.AttendanceStatus.HOLIDAY ? 1 : 0;
            int absent = (!weekend && !isHoliday && (st == null || st == Attendance.AttendanceStatus.ABSENT)) ? 1 : 0;

            days.add(new AnalyticsResponseDto.CalendarDay(
                    d, weekend, isHoliday, holidays.get(d),
                    present, late, absent, excused, holidayCount, 1));
        }

        return new AnalyticsResponseDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    // ── Attendance search by date ────────────────────────

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, int page, int size) {
        return searchByDate(cohortId, start, end, null, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, Integer lastNDays, int page, int size) {
        LocalDate[] range = resolveDateRange(start, end, lastNDays);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));

        Page<Attendance> result = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1], pageable)
                : attendanceRepository.findByDateBetween(range[0], range[1], pageable);

        return new AnalyticsResponseDto.PageResponse<>(buildRecords(result.getContent()),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Resolves the effective search range. When {@code lastNDays} is provided the
     * range is [today-(n-1) .. today]; otherwise the explicit start/end is used.
     */
    public LocalDate[] resolveDateRange(LocalDate start, LocalDate end, Integer lastNDays) {
        if (lastNDays != null && lastNDays > 0) {
            int n = Math.min(730, lastNDays); // cap at 2 years to protect the database
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            return new LocalDate[]{today.minusDays(n - 1), today};
        }
        if (start == null || end == null) throw AppException.badRequest("Start and end dates are required");
        if (end.isBefore(start)) throw AppException.badRequest("End date cannot be before start date");
        return new LocalDate[]{start, end};
    }

    /** Non-paginated list used for calendar / date-range exports. */
    public List<AttendanceResponseDto.AttendanceRecord> findRecordsInRange(String cohortId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1])
                : attendanceRepository.findByDateBetween(range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    public List<AttendanceResponseDto.AttendanceRecord> findStudentRecordsInRange(String studentId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    /**
     * Builds a single-student summary export row (attendance %, present, absent,
     * late, excused, holiday, days attended/missed, streaks and rating).
     */
    public AnalyticsResponseDto.StudentAnalytics buildStudentSummaryExport(String studentId) {
        return buildStudentAnalytics(studentId);
    }

    // ── Behaviour Analytics ──────────────────────────────

    public AnalyticsResponseDto.StudentAnalytics buildStudentAnalytics(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null
                ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByStudentIdOrderByDateAsc(studentId);
        Map<LocalDate, Attendance.AttendanceStatus> statusByDate = all.stream()
                .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a, LinkedHashMap::new));

        List<ExcuseRequest> excuses = excuseRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .collect(Collectors.toList());

        LocalDate creationDate = student.getCreatedAt() != null
                ? ZonedDateTime.ofInstant(student.getCreatedAt(), ZoneId.of(timezone)).toLocalDate() : today;
        LocalDate earliestAttDate = all.isEmpty() ? creationDate : all.get(0).getDate();
        LocalDate startDate = creationDate.isBefore(earliestAttDate) ? creationDate : earliestAttDate;
        if (startDate.isAfter(today)) startDate = today;

        Set<LocalDate> holidays = holidayService.holidayDatesBetween(startDate, today, cohortId);

        int schoolDays = 0, present = 0, late = 0, excused = 0, holiday = 0, absent = 0;
        int curAtt = 0, maxAtt = 0, curAbs = 0, maxAbs = 0;

        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            if (!holidayService.isSchoolDay(d, holidays)) continue;
            schoolDays++;
            final LocalDate currDate = d;
            Attendance.AttendanceStatus st = statusByDate.get(d);
            boolean isExcused = excuses.stream().anyMatch(e -> e.getStartDate() != null &&
                    !currDate.isBefore(e.getStartDate()) && !currDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)));

            if (st == Attendance.AttendanceStatus.PRESENT) {
                present++; curAtt++; curAbs = 0;
                if (curAtt > maxAtt) maxAtt = curAtt;
            } else if (st == Attendance.AttendanceStatus.LATE) {
                late++; curAtt++; curAbs = 0;
                if (curAtt > maxAtt) maxAtt = curAtt;
            } else if (st == Attendance.AttendanceStatus.EXCUSED || isExcused) {
                excused++;
            } else if (st == Attendance.AttendanceStatus.HOLIDAY) {
                schoolDays--;
                holiday++;
            } else {
                absent++; curAbs++; curAtt = 0;
                if (curAbs > maxAbs) maxAbs = curAbs;
            }
        }

        int attended = present + late;
        double rate = (schoolDays - excused) > 0 ? (double) attended / (schoolDays - excused) * 100.0
                : (attended > 0 ? 100.0 : 0.0);
        String rating = rate >= 90 ? "EXCELLENT" : rate >= 75 ? "GOOD" : rate >= 50 ? "FAIR" : "POOR";

        List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> trend = buildMonthlyTrend(today, statusByDate, holidays);

        return new AnalyticsResponseDto.StudentAnalytics(
                studentId, student.getName(), cohortId, cohortName,
                Math.round(rate * 10.0) / 10.0, schoolDays, present, late, absent, excused, holiday, late,
                maxAtt, maxAbs, rating, trend);
    }

    private List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> buildMonthlyTrend(
            LocalDate today, Map<LocalDate, Attendance.AttendanceStatus> statusByDate, Set<LocalDate> holidays) {
        List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> trend = new ArrayList<>();
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            LocalDate ms = monthStart.plusMonths(i);
            LocalDate me = ms.withDayOfMonth(ms.lengthOfMonth());
            int schoolDays = 0, attended = 0;
            for (LocalDate d = ms; !d.isAfter(me); d = d.plusDays(1)) {
                if (!holidayService.isSchoolDay(d, holidays)) continue;
                schoolDays++;
                Attendance.AttendanceStatus st = statusByDate.get(d);
                if (st == Attendance.AttendanceStatus.PRESENT || st == Attendance.AttendanceStatus.LATE) attended++;
            }
            double mRate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
            trend.add(new AnalyticsResponseDto.StudentAnalytics.MonthlyTrend(
                    ms.getMonth().toString().substring(0, 3), ms.getYear(), mRate));
        }
        return trend;
    }



    // ── Cohort export data ───────────────────────────────

    public List<AnalyticsResponseDto.CohortExportRow> buildCohortExportRows(String cohortId) {
        if (!cohortRepository.existsById(cohortId)) {
            throw AppException.notFound("Cohort not found");
        }
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByCohortId(cohortId);
        Map<String, List<Attendance>> byStudent = all.stream()
                .collect(Collectors.groupingBy(Attendance::getStudentId));

        LocalDate globalStart = today;
        for (List<Attendance> recs : byStudent.values()) {
            for (Attendance a : recs) {
                if (a.getDate().isBefore(globalStart)) globalStart = a.getDate();
            }
        }
        Set<LocalDate> holidays = holidayService.holidayDatesBetween(globalStart, today, cohortId);

        List<AnalyticsResponseDto.CohortExportRow> rows = new ArrayList<>();
        for (User s : students) {
            List<Attendance> recs = byStudent.getOrDefault(s.getId(), List.of());
            Map<LocalDate, Attendance.AttendanceStatus> statusByDate = recs.stream()
                    .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a));
            LocalDate startDate = recs.isEmpty() ? today : recs.stream()
                    .map(Attendance::getDate).min(LocalDate::compareTo).orElse(today);

            int schoolDays = 0, attended = 0, present = 0, late = 0, excused = 0, holidayDays = 0;
            for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
                if (holidays.contains(d)) { holidayDays++; continue; }
                if (d.getDayOfWeek().getValue() >= 6) continue;
                schoolDays++;
                Attendance.AttendanceStatus st = statusByDate.get(d);
                if (st == null) continue;
                switch (st) {
                    case PRESENT -> { present++; attended++; }
                    case LATE -> { late++; attended++; }
                    case EXCUSED -> excused++;
                    default -> {}
                }
            }
            double rate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
            rows.add(new AnalyticsResponseDto.CohortExportRow(
                    s.getName(), s.getRegistrationNumber(), rate, present, late,
                    excused, holidayDays, attended,
                    Math.max(0, schoolDays - attended - excused), schoolDays));
        }
        rows.sort((a, b) -> a.getStudentName().compareToIgnoreCase(b.getStudentName()));
        return rows;
    }

    // ── Helpers ──────────────────────────────────────────
    private Attendance.AttendanceStatus determineStatus(String cohortId) {
        ZoneId zone = ZoneId.of(timezone);
        LocalDate today = LocalDate.now(zone);
        if (holidayService.isHoliday(today, cohortId)) {
            return Attendance.AttendanceStatus.HOLIDAY;
        }
        LocalTime now = LocalTime.now(zone);
        LocalTime threshold;
        try {
            String thresholdVal = getSetting("late_threshold", lateThreshold);
            String[] parts = thresholdVal.split(":");
            threshold = LocalTime.of(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (Exception e) {
            log.warn("Invalid late_threshold setting. Defaulting to 08:31: {}", e.getMessage());
            threshold = LocalTime.of(8, 31);
        }
        return now.isBefore(threshold) ? Attendance.AttendanceStatus.PRESENT : Attendance.AttendanceStatus.LATE;
    }

    private String getCohortName(String cohortId) {
        return cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId);
    }

    public AttendanceResponseDto.AttendanceRecord toRecord(Attendance a) {
        User student = userRepository.findById(a.getStudentId()).orElse(null);
        Cohort cohort = a.getCohortId() != null ? cohortRepository.findById(a.getCohortId()).orElse(null) : null;
        return new AttendanceResponseDto.AttendanceRecord(
                a.getId(), a.getStudentId(),
                student != null ? student.getName() : a.getStudentId(),
                student != null ? student.getRegistrationNumber() : null,
                a.getCohortId(), cohort != null ? cohort.getName() : a.getCohortId(),
                a.getDate(), a.getMarkedAt(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.isManual(), a.getManualReason(), null
        );
    }

    /** Bulk record conversion with batched lookups to avoid N+1 queries. */
    public List<AttendanceResponseDto.AttendanceRecord> buildRecords(List<Attendance> records) {
        if (records.isEmpty()) return List.of();

        Set<String> studentIds = records.stream().map(Attendance::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> cohortIds = records.stream().map(Attendance::getCohortId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> deviceIds = records.stream().map(Attendance::getDeviceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, User> students = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<String, Cohort> cohorts = cohortRepository.findAllById(cohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));
        Map<String, Device> devices = deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity(), (a, b) -> a));

        return records.stream().map(a -> {
            User s = students.get(a.getStudentId());
            Cohort c = a.getCohortId() != null ? cohorts.get(a.getCohortId()) : null;
            Device d = a.getDeviceId() != null ? devices.get(a.getDeviceId()) : null;
            String deviceUsed = d != null
                    ? (d.getFingerprint() != null ? d.getFingerprint() : d.getImei()) : null;
            boolean isWeekend = a.getDate() != null && a.getDate().getDayOfWeek().getValue() >= 6;
            String status = isWeekend && a.getStatus() == Attendance.AttendanceStatus.ABSENT
                    ? "WEEKEND"
                    : (a.getStatus() != null ? a.getStatus().name() : null);
            return new AttendanceResponseDto.AttendanceRecord(
                    a.getId(), a.getStudentId(),
                    s != null ? s.getName() : a.getStudentId(),
                    s != null ? s.getRegistrationNumber() : null,
                    a.getCohortId(), c != null ? c.getName() : a.getCohortId(),
                    a.getDate(), a.getMarkedAt(),
                    status,
                    a.isManual(), a.getManualReason(), deviceUsed);
        }).collect(Collectors.toList());
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.ManualStudentAttendanceResponse> getManualAttendancePage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, int page, int size) {
        
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));

        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank())
                ? List.of(cohortId) : assignedCohortIds;

        if (targetCohortIds.isEmpty()) {
            return new AnalyticsResponseDto.PageResponse<>(List.of(), page, size, 0, 1);
        }

        List<User> students = userRepository.findByCohortIdIn(targetCohortIds);
        String q = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!q.isEmpty()) {
            students = students.stream().filter(s ->
                (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                (s.getEmail() != null && s.getEmail().toLowerCase().contains(q)) ||
                (s.getRegistrationNumber() != null && s.getRegistrationNumber().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        int total = students.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<User> pagedStudents = students.subList(from, to);
        Set<String> pagedStudentIds = pagedStudents.stream().map(User::getId).collect(Collectors.toSet());

        List<Attendance> existingAtt = pagedStudentIds.isEmpty() ? List.of()
                : attendanceRepository.findByStudentIdIn(pagedStudentIds).stream()
                .filter(a -> date.equals(a.getDate()))
                .collect(Collectors.toList());
        Map<String, Attendance> attByStudent = existingAtt.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

        List<ExcuseRequest> excuses = pagedStudentIds.isEmpty() ? List.of()
                : excuseRepository.findByStudentIdIn(pagedStudentIds).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate()) && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, Function.identity(), (a, b) -> a));

        Map<String, Cohort> cohortsById = cohortRepository.findAllById(targetCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));

        List<AttendanceResponseDto.ManualStudentAttendanceResponse> content = pagedStudents.stream().map(s -> {
            Cohort c = s.getCohortId() != null ? cohortsById.get(s.getCohortId()) : null;
            Attendance a = attByStudent.get(s.getId());
            ExcuseRequest exc = excuseByStudent.get(s.getId());

            String status;
            Instant markedAt = null;
            boolean manual = false;
            String manualReason = null;

            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
            if (isWeekend) {
                status = (a != null && a.getStatus() != null && a.getStatus() != Attendance.AttendanceStatus.ABSENT) ? a.getStatus().name() : "WEEKEND";
                markedAt = a != null ? a.getMarkedAt() : null;
                manual = a != null && a.isManual();
                manualReason = a != null ? a.getManualReason() : null;
            } else if (a != null) {
                status = a.getStatus() != null ? a.getStatus().name() : "ABSENT";
                markedAt = a.getMarkedAt();
                manual = a.isManual();
                manualReason = a.getManualReason();
            } else if (exc != null) {
                status = "EXCUSED";
                manualReason = "Approved excuse: " + exc.getReason();
            } else {
                status = "ABSENT";
            }

            return new AttendanceResponseDto.ManualStudentAttendanceResponse(
                    s.getId(), s.getName(), s.getRegistrationNumber(), s.getEmail(),
                    s.getCohortId(), c != null ? c.getName() : s.getCohortId(),
                    date, status, markedAt, manual, manualReason
            );
        }).collect(Collectors.toList());

        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, int page, int size) {
        return getFacilitatorReportPage(assignedCohortIds, cohortId, queryStr, targetDate, null, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, int page, int size) {
        
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank())
                ? List.of(cohortId) : assignedCohortIds;

        if (targetCohortIds.isEmpty()) {
            return new AnalyticsResponseDto.PageResponse<>(List.of(), page, size, 0, 1);
        }

        List<User> students = userRepository.findByCohortIdIn(targetCohortIds);
        String q = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!q.isEmpty()) {
            students = students.stream().filter(s ->
                (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                (s.getEmail() != null && s.getEmail().toLowerCase().contains(q)) ||
                (s.getRegistrationNumber() != null && s.getRegistrationNumber().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        Set<String> studentIds = students.stream().map(User::getId).collect(Collectors.toSet());

        List<Attendance> existingAtt = studentIds.isEmpty() ? List.of()
                : attendanceRepository.findByStudentIdIn(studentIds).stream()
                .filter(a -> date.equals(a.getDate()))
                .collect(Collectors.toList());
        Map<String, Attendance> attByStudent = existingAtt.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

        List<ExcuseRequest> excuses = studentIds.isEmpty() ? List.of()
                : excuseRepository.findByStudentIdIn(studentIds).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate()) && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, Function.identity(), (a, b) -> a));

        Map<String, Cohort> cohortsById = cohortRepository.findAllById(targetCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));

        List<AttendanceResponseDto.AttendanceRecord> allRecords = students.stream().map(s -> {
            Cohort c = s.getCohortId() != null ? cohortsById.get(s.getCohortId()) : null;
            Attendance a = attByStudent.get(s.getId());
            ExcuseRequest exc = excuseByStudent.get(s.getId());

            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
            String status = isWeekend
                    ? ((a != null && a.getStatus() != null && a.getStatus() != Attendance.AttendanceStatus.ABSENT) ? a.getStatus().name() : "WEEKEND")
                    : (a != null ? (a.getStatus() != null ? a.getStatus().name() : "ABSENT")
                                 : (exc != null ? "EXCUSED" : "ABSENT"));

            return new AttendanceResponseDto.AttendanceRecord(
                    a != null ? a.getId() : null,
                    s.getId(),
                    s.getName(),
                    s.getRegistrationNumber(),
                    s.getCohortId(),
                    c != null ? c.getName() : s.getCohortId(),
                    date,
                    a != null ? a.getMarkedAt() : null,
                    status,
                    a != null && a.isManual(),
                    a != null ? a.getManualReason() : null,
                    null
            );
        }).collect(Collectors.toList());

        // Status Filtering
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            String sf = statusFilter.trim().toUpperCase();
            allRecords = allRecords.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().equalsIgnoreCase(sf))
                    .collect(Collectors.toList());
        }

        // Two-tier sorting: Attended (Early/Present/Late) ordered by markedAt ASCENDING (earliest first), Absent/Excused sorted A-Z by full name
        sortFacilitatorAttendanceRecords(allRecords);

        int total = allRecords.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<AttendanceResponseDto.AttendanceRecord> pagedContent = allRecords.subList(from, to);

        return new AnalyticsResponseDto.PageResponse<>(pagedContent, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public org.springframework.http.ResponseEntity<byte[]> exportPublicProjectionReport(
            String cohortId, LocalDate targetDate, String format, String clientIp, ExportService exportService) {
        
        if (cohortId == null || cohortId.isBlank()) {
            throw AppException.badRequest("Cohort ID is required");
        }

        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        LocalDate date = targetDate != null ? targetDate : today;
        return exportFacilitatorReport(
                "PUBLIC_PROJECTION", List.of(cohortId), cohortId, null, date, null, format, "projection", exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, LocalDate targetDate, String format, ExportService exportService) {
        return exportFacilitatorReport(null, assignedCohortIds, cohortId, null, targetDate, null, format, null, exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, String format, ExportService exportService) {
        return exportFacilitatorReport(null, assignedCohortIds, cohortId, queryStr, targetDate, statusFilter, format, null, exportService);
    }

    public org.springframework.http.ResponseEntity<byte[]> exportFacilitatorReport(
            String actorId, List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, String statusFilter, String format, String source, ExportService exportService) {
        
        boolean isProjection = source != null && ("projection".equalsIgnoreCase(source.trim()) || "projection_screen".equalsIgnoreCase(source.trim()));

        if (isProjection) {
            // TODO: TOCTOU Race Condition — the rate limit check is performed here before generating the export,
            // while the audit log entry recording the download is created after the report generation succeeds.
            // Under high concurrency, simultaneous requests may pass this check before any audit log is saved.
            // Since this is a soft usage limit for projection report exports and not a security boundary, it is accepted as low-risk.
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            Instant startOfDay = today.atStartOfDay(ZoneId.of(timezone)).toInstant();
            Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant();

            long todayCountByActor = (actorId != null && !"PUBLIC_PROJECTION".equals(actorId))
                    ? auditLogRepository.countByActorIdAndActionAndCreatedAtBetween(actorId, AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, startOfDay, endOfDay)
                    : 0;
            long todayCountByTarget = (cohortId != null && !cohortId.isBlank())
                    ? auditLogRepository.countByTargetIdAndActionAndCreatedAtBetween(cohortId, AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, startOfDay, endOfDay)
                    : 0;

            if (todayCountByActor >= 3 || todayCountByTarget >= 3) {
                throw AppException.badRequest("You have reached today's Projection Screen download limit (3 downloads). Please try again tomorrow.");
            }
        }

        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank())
                ? List.of(cohortId) : assignedCohortIds;

        List<User> students = userRepository.findByCohortIdIn(targetCohortIds);
        String q = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!q.isEmpty()) {
            students = students.stream().filter(s ->
                (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                (s.getEmail() != null && s.getEmail().toLowerCase().contains(q)) ||
                (s.getRegistrationNumber() != null && s.getRegistrationNumber().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        Set<String> studentIds = students.stream().map(User::getId).collect(Collectors.toSet());
        Map<String, User> studentMap = students.stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<Attendance> existingAtt = studentIds.isEmpty() ? List.of()
                : attendanceRepository.findByStudentIdIn(studentIds).stream()
                .filter(a -> date.equals(a.getDate()))
                .collect(Collectors.toList());
        Map<String, Attendance> attByStudent = existingAtt.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

        List<ExcuseRequest> excuses = studentIds.isEmpty() ? List.of()
                : excuseRepository.findByStudentIdIn(studentIds).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate()) && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, Function.identity(), (a, b) -> a));

        Map<String, Cohort> cohortsById = cohortRepository.findAllById(targetCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));

        List<AttendanceResponseDto.AttendanceRecord> allRecords = students.stream().map(s -> {
            Cohort c = s.getCohortId() != null ? cohortsById.get(s.getCohortId()) : null;
            Attendance a = attByStudent.get(s.getId());
            ExcuseRequest exc = excuseByStudent.get(s.getId());

            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
            String status = isWeekend
                    ? ((a != null && a.getStatus() != null && a.getStatus() != Attendance.AttendanceStatus.ABSENT) ? a.getStatus().name() : "WEEKEND")
                    : (a != null ? (a.getStatus() != null ? a.getStatus().name() : "ABSENT")
                                 : (exc != null ? "EXCUSED" : "ABSENT"));

            return new AttendanceResponseDto.AttendanceRecord(
                    a != null ? a.getId() : null,
                    s.getId(),
                    s.getName(),
                    s.getRegistrationNumber(),
                    s.getCohortId(),
                    c != null ? c.getName() : s.getCohortId(),
                    date,
                    a != null ? a.getMarkedAt() : null,
                    status,
                    a != null && a.isManual(),
                    a != null ? a.getManualReason() : null,
                    null
            );
        }).collect(Collectors.toList());

        // Status Filtering
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            String sf = statusFilter.trim().toUpperCase();
            allRecords = allRecords.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().equalsIgnoreCase(sf))
                    .collect(Collectors.toList());
        }

        // Two-tier sorting: Attended (Early/Present/Late) ordered by markedAt ASCENDING (earliest first), Absent/Excused sorted A-Z by full name
        sortFacilitatorAttendanceRecords(allRecords);

        List<List<Object>> table = new ArrayList<>();
        java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of(timezone));

        for (AttendanceResponseDto.AttendanceRecord r : allRecords) {
            String checkInTime = (r.getMarkedAt() != null) ? timeFmt.format(r.getMarkedAt()) : "—";

            table.add(List.of(
                    r.getStudentName() != null ? r.getStudentName() : "",
                    r.getCohortName() != null ? r.getCohortName() : "",
                    r.getStatus() != null ? r.getStatus() : "",
                    date.toString(),
                    checkInTime,
                    r.getRegistrationNumber() != null ? r.getRegistrationNumber() : "",
                    r.getManualReason() != null ? r.getManualReason() : "N/A"
            ));
        }

        String fmt = (format != null && !format.isBlank()) ? format : "xlsx";
        org.springframework.http.ResponseEntity<byte[]> response = exportService.export(
                List.of("Student Name", "Cohort", "Attendance Status", "Attendance Date", "Attendance Time", "Registration Number", "Excuse Status"),
                table, fmt, "attendance_report_" + date);

        if (isProjection && response.getStatusCode().is2xxSuccessful()) {
            User actor = userRepository.findById(actorId).orElse(null);
            auditService.log(actorId, actor != null ? actor.getName() : actorId, actor != null ? actor.getRole().name() : "FACILITATOR",
                    AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, cohortId, cohortId,
                    "Projection Screen report download for cohort " + cohortId, null);
        }

        return response;
    }

    public static void sortFacilitatorAttendanceRecords(List<AttendanceResponseDto.AttendanceRecord> records) {
        records.sort((r1, r2) -> {
            Instant t1 = r1.getMarkedAt();
            Instant t2 = r2.getMarkedAt();

            // Primary sort: attendance timestamp ascending (earliest attendance first)
            if (t1 != null && t2 != null) {
                int cmp = t1.compareTo(t2);
                if (cmp != 0) return cmp;
            } else if (t1 != null) {
                return -1;
            } else if (t2 != null) {
                return 1;
            }

            // Secondary sort: student full name ascending (A-Z)
            String n1 = r1.getStudentName() != null ? r1.getStudentName() : "";
            String n2 = r2.getStudentName() != null ? r2.getStudentName() : "";
            return n1.compareToIgnoreCase(n2);
        });
    }

    public static boolean isAttendedRecord(AttendanceResponseDto.AttendanceRecord r) {
        if (r == null || r.getStatus() == null) return false;
        String status = r.getStatus().toUpperCase();
        return "PRESENT".equals(status) || "LATE".equals(status) || "EARLY".equals(status)
                || (r.getMarkedAt() != null && !"ABSENT".equals(status) && !"EXCUSED".equals(status) && !"WEEKEND".equals(status) && !"HOLIDAY".equals(status));
    }
}
