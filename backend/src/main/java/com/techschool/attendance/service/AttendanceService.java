package com.techschool.attendance.service;

import com.techschool.attendance.dto.AnalyticsDto;
import com.techschool.attendance.dto.AuthDto;
import com.techschool.attendance.dto.AttendanceDto;
import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
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
    private final AuthService authService;
    private final HolidayService holidayService;

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

        // 7. Determine status (holidays are automatically recorded as HOLIDAY, never ABSENT)
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
        return buildRecords(attendanceRepository.findByStudentId(studentId));
    }

    public AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord> getStudentHistoryPage(
            String studentId, int page, int size) {
        List<Attendance> sorted = attendanceRepository.findByStudentId(studentId).stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .collect(Collectors.toList());

        int total = sorted.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<AttendanceDto.AttendanceRecord> content =
                buildRecords(sorted.subList(from, to));
        return new AnalyticsDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public AttendanceDto.DailySummary getCohortSummaryToday(String cohortId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        return buildDailySummary(cohortId, today);
    }

    public AttendanceDto.DailySummary buildDailySummary(String cohortId, LocalDate date) {
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

        return new AttendanceDto.DailySummary(
                date, cohortId, cohortName,
                total, present, late, absent, excused, holiday, manual, rate,
                buildRecords(records)
        );
    }

    // ── Calendar ─────────────────────────────────────────

    public AnalyticsDto.CalendarMonth buildCalendarMonth(String cohortId, int year, int month) {
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

        List<AnalyticsDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            Optional<HolidayService.HolidayInfo> holiday =
                    holidayService.findHoliday(d, cohortId != null && !cohortId.isBlank() ? cohortId : null);

            List<Attendance> dayRecs = byDay.getOrDefault(d, List.of());
            int present = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int excused = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            int holidayCount = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
            int absent = weekend || holiday.isPresent()
                    ? 0
                    : Math.max(0, totalStudents - dayRecs.size());

            days.add(new AnalyticsDto.CalendarDay(
                    d, weekend, holiday.isPresent(),
                    holiday.map(HolidayService.HolidayInfo::name).orElse(null),
                    present, late, absent, excused, holidayCount, totalStudents));
        }

        return new AnalyticsDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    public AnalyticsDto.CalendarMonth buildStudentCalendarMonth(String studentId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null
                ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";

        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, first, last);
        Map<LocalDate, Attendance> byDay = records.stream()
                .collect(Collectors.toMap(Attendance::getDate, Function.identity()));

        List<AnalyticsDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            Optional<HolidayService.HolidayInfo> holiday = holidayService.findHoliday(d, cohortId);
            Attendance rec = byDay.get(d);
            Attendance.AttendanceStatus st = rec != null ? rec.getStatus() : null;

            int present = st == Attendance.AttendanceStatus.PRESENT ? 1 : 0;
            int late = st == Attendance.AttendanceStatus.LATE ? 1 : 0;
            int excused = st == Attendance.AttendanceStatus.EXCUSED ? 1 : 0;
            int holidayCount = st == Attendance.AttendanceStatus.HOLIDAY ? 1 : 0;
            int absent = (!weekend && holiday.isEmpty() && st == null) ? 1 : 0;

            days.add(new AnalyticsDto.CalendarDay(
                    d, weekend, holiday.isPresent(),
                    holiday.map(HolidayService.HolidayInfo::name).orElse(null),
                    present, late, absent, excused, holidayCount, 1));
        }

        return new AnalyticsDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    // ── Attendance search by date ────────────────────────

    public AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, int page, int size) {
        return searchByDate(cohortId, start, end, null, page, size);
    }

    public AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord> searchByDate(
            String cohortId, LocalDate start, LocalDate end, Integer lastNDays, int page, int size) {
        LocalDate[] range = resolveDateRange(start, end, lastNDays);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));

        Page<Attendance> result = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1], pageable)
                : attendanceRepository.findByDateBetween(range[0], range[1], pageable);

        return new AnalyticsDto.PageResponse<>(buildRecords(result.getContent()),
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
    public List<AttendanceDto.AttendanceRecord> findRecordsInRange(String cohortId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1])
                : attendanceRepository.findByDateBetween(range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    public List<AttendanceDto.AttendanceRecord> findStudentRecordsInRange(String studentId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    /**
     * Builds a single-student summary export row (attendance %, present, absent,
     * late, excused, holiday, days attended/missed, streaks and rating).
     */
    public AnalyticsDto.StudentAnalytics buildStudentSummaryExport(String studentId) {
        return buildStudentAnalytics(studentId);
    }

    // ── Behaviour Analytics ──────────────────────────────

    public AnalyticsDto.StudentAnalytics buildStudentAnalytics(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null
                ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByStudentIdOrderByDateAsc(studentId);
        Map<LocalDate, Attendance.AttendanceStatus> statusByDate = all.stream()
                .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a, LinkedHashMap::new));

        int present = count(all, Attendance.AttendanceStatus.PRESENT);
        int late = count(all, Attendance.AttendanceStatus.LATE);
        int excused = count(all, Attendance.AttendanceStatus.EXCUSED);
        int holiday = count(all, Attendance.AttendanceStatus.HOLIDAY);
        int absent = count(all, Attendance.AttendanceStatus.ABSENT);

        LocalDate startDate = all.isEmpty() ? today : all.get(0).getDate();

        int schoolDays = 0, attended = 0, curAtt = 0, maxAtt = 0, curAbs = 0, maxAbs = 0;
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            if (!holidayService.isSchoolDay(d, cohortId)) continue;
            schoolDays++;
            Attendance.AttendanceStatus st = statusByDate.get(d);
            if (st == Attendance.AttendanceStatus.PRESENT || st == Attendance.AttendanceStatus.LATE) {
                attended++; curAtt++; curAbs = 0;
                if (curAtt > maxAtt) maxAtt = curAtt;
            } else if (st == Attendance.AttendanceStatus.EXCUSED) {
                // excused days neither extend nor break streaks
            } else {
                curAbs++; curAtt = 0;
                if (curAbs > maxAbs) maxAbs = curAbs;
            }
        }

        double rate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
        String rating = rate >= 90 ? "EXCELLENT" : rate >= 75 ? "GOOD" : rate >= 50 ? "FAIR" : "POOR";

        List<AnalyticsDto.StudentAnalytics.MonthlyTrend> trend = buildMonthlyTrend(cohortId, today, statusByDate);

        return new AnalyticsDto.StudentAnalytics(
                studentId, student.getName(), cohortId, cohortName,
                rate, all.size(), present, late, absent, excused, holiday, late,
                maxAtt, maxAbs, rating, trend);
    }

    private List<AnalyticsDto.StudentAnalytics.MonthlyTrend> buildMonthlyTrend(
            String cohortId, LocalDate today, Map<LocalDate, Attendance.AttendanceStatus> statusByDate) {
        List<AnalyticsDto.StudentAnalytics.MonthlyTrend> trend = new ArrayList<>();
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            LocalDate ms = monthStart.plusMonths(i);
            LocalDate me = ms.withDayOfMonth(ms.lengthOfMonth());
            int schoolDays = 0, attended = 0;
            for (LocalDate d = ms; !d.isAfter(me); d = d.plusDays(1)) {
                if (!holidayService.isSchoolDay(d, cohortId)) continue;
                schoolDays++;
                Attendance.AttendanceStatus st = statusByDate.get(d);
                if (st == Attendance.AttendanceStatus.PRESENT || st == Attendance.AttendanceStatus.LATE) attended++;
            }
            double mRate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
            trend.add(new AnalyticsDto.StudentAnalytics.MonthlyTrend(
                    ms.getMonth().toString().substring(0, 3), ms.getYear(), mRate));
        }
        return trend;
    }

    private int count(List<Attendance> records, Attendance.AttendanceStatus status) {
        return (int) records.stream().filter(a -> a.getStatus() == status).count();
    }

    // ── Cohort export data ───────────────────────────────

    public List<AnalyticsDto.CohortExportRow> buildCohortExportRows(String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByCohortId(cohortId);
        Map<String, List<Attendance>> byStudent = all.stream()
                .collect(Collectors.groupingBy(Attendance::getStudentId));

        List<AnalyticsDto.CohortExportRow> rows = new ArrayList<>();
        for (User s : students) {
            List<Attendance> recs = byStudent.getOrDefault(s.getId(), List.of());
            Map<LocalDate, Attendance.AttendanceStatus> statusByDate = recs.stream()
                    .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a));
            LocalDate startDate = recs.isEmpty() ? today : recs.stream()
                    .map(Attendance::getDate).min(LocalDate::compareTo).orElse(today);

            int schoolDays = 0, attended = 0, present = 0, late = 0, excused = 0, holidayDays = 0;
            for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
                if (holidayService.findHoliday(d, cohortId).isPresent()) { holidayDays++; continue; }
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
            rows.add(new AnalyticsDto.CohortExportRow(
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
                a.isManual(), a.getManualReason(), null
        );
    }

    /** Bulk record conversion with batched lookups to avoid N+1 queries. */
    public List<AttendanceDto.AttendanceRecord> buildRecords(List<Attendance> records) {
        if (records.isEmpty()) return List.of();

        Set<String> studentIds = records.stream().map(Attendance::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> cohortIds = records.stream().map(Attendance::getCohortId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> deviceIds = records.stream().map(Attendance::getDeviceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, User> students = userRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Cohort> cohorts = cohortRepository.findAllById(cohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity()));
        Map<String, Device> devices = deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));

        return records.stream().map(a -> {
            User s = students.get(a.getStudentId());
            Cohort c = a.getCohortId() != null ? cohorts.get(a.getCohortId()) : null;
            Device d = a.getDeviceId() != null ? devices.get(a.getDeviceId()) : null;
            String deviceUsed = d != null
                    ? (d.getFingerprint() != null ? d.getFingerprint() : d.getImei()) : null;
            return new AttendanceDto.AttendanceRecord(
                    a.getId(), a.getStudentId(),
                    s != null ? s.getName() : a.getStudentId(),
                    a.getCohortId(), c != null ? c.getName() : a.getCohortId(),
                    a.getDate(), a.getMarkedAt(),
                    a.getStatus() != null ? a.getStatus().name() : null,
                    a.isManual(), a.getManualReason(), deviceUsed);
        }).collect(Collectors.toList());
    }
}
