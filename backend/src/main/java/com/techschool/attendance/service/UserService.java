package com.techschool.attendance.service;

import com.techschool.attendance.dto.AnalyticsDto;
import com.techschool.attendance.dto.UserDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AttendanceService attendanceService;

    public UserDto.UserResponse createUser(String actorId, String actorName, String actorRole,
                                            UserDto.CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("Email already registered: " + request.getEmail());
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setCohortId(request.getCohortId());
        user.setRegistrationNumber(request.getRegistrationNumber());
        user.setAssignedCohortIds(request.getAssignedCohortIds());
        user.setActive(true);
        User saved = userRepository.save(user);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.USER_CREATED, saved.getId(), saved.getName(),
                saved.getRole() + " account created", null);

        return toResponse(saved);
    }

    public List<UserDto.UserResponse> getUsersByRole(User.Role role) {
        List<User> users = userRepository.findByRole(role);
        if (users.isEmpty()) return List.of();
        Map<String, Cohort> cohortsById = loadCohortsById(users);
        Map<String, Device> devicesByStudent = loadDevicesByStudent(users);
        Map<String, UserDto.UserResponse.AttendanceSummary> summariesByStudent =
                role == User.Role.STUDENT ? loadSummariesByStudent(users) : Map.of();
        return users.stream()
                .map(u -> toResponse(u, cohortsById, devicesByStudent, summariesByStudent))
                .collect(Collectors.toList());
    }

    // ── Student Search & Pagination ─────────────────────
    public AnalyticsDto.PageResponse<UserDto.UserResponse> searchStudents(String cohortId, String query,
                                                                           int page, int size) {
        return searchStudents(cohortId, query, page, size, "name", "asc");
    }

    public AnalyticsDto.PageResponse<UserDto.UserResponse> searchStudents(String cohortId, String query,
                                                                           int page, int size,
                                                                           String sort, String order) {
        List<User> candidates = (cohortId != null && !cohortId.isBlank())
                ? userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT)
                : userRepository.findByRole(User.Role.STUDENT);

        String q = query == null ? "" : query.trim().toLowerCase();
        List<User> filtered = q.isEmpty() ? candidates : candidates.stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q))
                        || (u.getRegistrationNumber() != null && u.getRegistrationNumber().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        // Batched lookups — replaces per-student N+1 queries.
        Map<String, Cohort> cohortsById = loadCohortsById(filtered);
        Map<String, Device> devicesByStudent = loadDevicesByStudent(filtered);
        Map<String, UserDto.UserResponse.AttendanceSummary> summariesByStudent = loadSummariesByStudent(filtered);

        boolean asc = !"desc".equalsIgnoreCase(order);
        filtered.sort(comparatorFor(sort, filtered, asc, summariesByStudent));

        int total = filtered.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<UserDto.UserResponse> content = filtered.subList(from, to).stream()
                .map(u -> toResponse(u, cohortsById, devicesByStudent, summariesByStudent))
                .collect(Collectors.toList());

        return new AnalyticsDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public AnalyticsDto.PageResponse<UserDto.UserResponse> searchDevices(
            String query, int page, int size, String sort, String order) {
        List<User> candidates = userRepository.findByRole(User.Role.STUDENT);
        String q = query == null ? "" : query.trim().toLowerCase();
        List<User> filtered = q.isEmpty() ? candidates : candidates.stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        Map<String, Cohort> cohortsById = loadCohortsById(filtered);
        Map<String, Device> devicesByStudent = loadDevicesByStudent(filtered);
        Map<String, UserDto.UserResponse.AttendanceSummary> summariesByStudent = loadSummariesByStudent(filtered);

        int total = filtered.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<UserDto.UserResponse> content = filtered.subList(from, to).stream()
                .map(u -> toResponse(u, cohortsById, devicesByStudent, summariesByStudent))
                .collect(Collectors.toList());

        return new AnalyticsDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public AnalyticsDto.PageResponse<UserDto.StudentAttendanceResponse> searchStudentsAdmin(
            String cohortId, String query,
            LocalDate startDate, LocalDate endDate,
            String statusStr,
            int page, int size,
            String sort, String order) {

        List<User> candidates = (cohortId != null && !cohortId.isBlank())
                ? userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT)
                : userRepository.findByRole(User.Role.STUDENT);

        String q = query == null ? "" : query.trim().toLowerCase();
        List<User> filtered = q.isEmpty() ? candidates : candidates.stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q))
                        || (u.getRegistrationNumber() != null && u.getRegistrationNumber().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        Attendance.AttendanceStatus statusFilter = null;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr)) {
            try {
                statusFilter = Attendance.AttendanceStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (Exception ignored) {}
        }

        final Attendance.AttendanceStatus targetStatus = statusFilter;
        final LocalDate effStart = startDate;
        final LocalDate effEnd = endDate;

        if (targetStatus != null || effStart != null || effEnd != null) {
            Set<String> matchingStudentIds;
            if (effStart != null && effEnd != null) {
                List<Attendance> inRange = attendanceRepository.findByDateBetween(effStart, effEnd);
                matchingStudentIds = inRange.stream()
                        .filter(a -> targetStatus == null || a.getStatus() == targetStatus)
                        .map(Attendance::getStudentId)
                        .collect(Collectors.toSet());
            } else if (targetStatus != null) {
                List<Attendance> allRecs = attendanceRepository.findAll();
                matchingStudentIds = allRecs.stream()
                        .filter(a -> a.getStatus() == targetStatus)
                        .map(Attendance::getStudentId)
                        .collect(Collectors.toSet());
            } else {
                matchingStudentIds = Set.of();
            }

            if (targetStatus != null) {
                filtered = filtered.stream()
                        .filter(u -> matchingStudentIds.contains(u.getId()))
                        .collect(Collectors.toList());
            }
        }

        Map<String, Cohort> cohortsById = loadCohortsById(filtered);
        Map<String, UserDto.StudentAttendanceResponse> responsesByStudent =
                loadStudentAttendanceResponses(filtered, effStart, effEnd, cohortsById);

        boolean asc = !"desc".equalsIgnoreCase(order);
        String sortKey = sort == null ? "name" : sort.toLowerCase().trim();
        Comparator<User> cmp;
        switch (sortKey) {
            case "email":
                cmp = Comparator.comparing(User::getEmail, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "registrationnumber":
            case "registration":
                cmp = Comparator.comparing(User::getRegistrationNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "rate":
            case "attendancerate":
            case "attendance":
                cmp = Comparator.comparing(u -> {
                    UserDto.StudentAttendanceResponse r = responsesByStudent.get(u.getId());
                    return r != null ? r.getAttendanceRate() : 0.0;
                });
                break;
            default:
                cmp = Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        filtered.sort(asc ? cmp : cmp.reversed());

        int total = filtered.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<UserDto.StudentAttendanceResponse> content = filtered.subList(from, to).stream()
                .map(u -> responsesByStudent.get(u.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new AnalyticsDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    private Map<String, UserDto.StudentAttendanceResponse> loadStudentAttendanceResponses(
            List<User> students, LocalDate startDate, LocalDate endDate, Map<String, Cohort> cohortsById) {
        Set<String> ids = students.stream().map(User::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();

        List<Attendance> all;
        if (startDate != null && endDate != null) {
            all = attendanceRepository.findByStudentIdInAndDateBetween(ids, startDate, endDate);
        } else {
            all = attendanceRepository.findByStudentIdIn(ids);
        }

        Map<String, List<Attendance>> byStudent = all.stream()
                .collect(Collectors.groupingBy(Attendance::getStudentId));

        Map<String, UserDto.StudentAttendanceResponse> out = new HashMap<>();
        for (User u : students) {
            List<Attendance> att = byStudent.getOrDefault(u.getId(), List.of());
            int present = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int absent = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int excused = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            int holiday = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
            int totalDays = att.size();

            double rate = totalDays > 0 ? (double) (present + late) / totalDays * 100.0 : 0.0;
            String rating = rate >= 90 ? "EXCELLENT" : rate >= 75 ? "GOOD" : rate >= 50 ? "FAIR" : "POOR";

            LocalDate lastDate = att.stream()
                    .map(Attendance::getDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            Cohort c = u.getCohortId() != null ? cohortsById.get(u.getCohortId()) : null;
            String cohortName = c != null ? c.getName() : (u.getCohortId() != null ? u.getCohortId() : "—");

            UserDto.StudentAttendanceResponse resp = new UserDto.StudentAttendanceResponse(
                    u.getId(),
                    u.getName(),
                    u.getRegistrationNumber(),
                    u.getEmail(),
                    u.getCohortId(),
                    cohortName,
                    Math.round(rate * 10.0) / 10.0,
                    present,
                    absent,
                    excused,
                    late,
                    holiday,
                    totalDays,
                    rating,
                    lastDate,
                    u.isActive(),
                    u.getCreatedAt()
            );
            out.put(u.getId(), resp);
        }
        return out;
    }

    public void deleteStudent(String actorId, String actorName, String actorRole, String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        if (student.getRole() != User.Role.STUDENT) {
            throw AppException.badRequest("User is not a student");
        }

        // Clean up attendance records
        List<Attendance> atts = attendanceRepository.findByStudentId(studentId);
        if (!atts.isEmpty()) {
            attendanceRepository.deleteAll(atts);
        }

        // Clean up device assignment
        deviceRepository.findByStudentId(studentId).ifPresent(deviceRepository::delete);

        // Delete user
        userRepository.delete(student);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.USER_DELETED, studentId, student.getName(),
                "Student account and associated attendance data deleted", null);
    }

    public ResponseEntity<byte[]> exportStudentsAdmin(String cohortId, String query,
                                                     LocalDate startDate, LocalDate endDate,
                                                     String status, String format, ExportService exportService) {
        AnalyticsDto.PageResponse<UserDto.StudentAttendanceResponse> page =
                searchStudentsAdmin(cohortId, query, startDate, endDate, status, 0, 10000, "name", "asc");

        List<List<Object>> table = new java.util.ArrayList<>();
        for (UserDto.StudentAttendanceResponse r : page.getContent()) {
            table.add(List.of(
                    r.getName() != null ? r.getName() : "",
                    r.getRegistrationNumber() != null ? r.getRegistrationNumber() : "",
                    r.getEmail() != null ? r.getEmail() : "",
                    r.getCohortName() != null ? r.getCohortName() : "",
                    String.format("%.1f", r.getAttendanceRate()) + "%",
                    String.valueOf(r.getPresentDays()),
                    String.valueOf(r.getAbsentDays()),
                    String.valueOf(r.getExcusedDays()),
                    String.valueOf(r.getLateDays()),
                    String.valueOf(r.getHolidayCount()),
                    String.valueOf(r.getTotalAttendanceDays()),
                    r.getRating() != null ? r.getRating() : "",
                    r.getLastAttendanceDate() != null ? r.getLastAttendanceDate().toString() : "N/A"
            ));
        }

        List<String> headers = List.of(
                "Student Name", "Registration No", "Email", "Cohort",
                "Attendance %", "Present Days", "Absent Days", "Excused Days",
                "Late Days", "Holiday Count", "Total Days", "Rating", "Last Attendance Date"
        );

        String baseName = "students_attendance";
        if (cohortId != null && !cohortId.isBlank()) {
            baseName += "_cohort_" + cohortId;
        }

        return exportService.export(headers, table, format, baseName);
    }

    /**
     * Builds a comparator for the requested sort key. Attendance-rate sorting uses
     * the already-batched summary map rather than one query per student.
     */
    private Comparator<User> comparatorFor(String sort, List<User> users, boolean asc,
                                           Map<String, UserDto.UserResponse.AttendanceSummary> summaries) {
        String sortKey = sort == null ? "name" : sort.toLowerCase().trim();
        Comparator<User> cmp;
        switch (sortKey) {
            case "email":
                cmp = Comparator.comparing(User::getEmail,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "registrationnumber":
            case "registration":
                cmp = Comparator.comparing(User::getRegistrationNumber,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "rate":
            case "attendanceRate":
            case "attendance":
                cmp = Comparator.comparing((User u) -> {
                    UserDto.UserResponse.AttendanceSummary s = summaries.get(u.getId());
                    return s != null ? s.getRate() : 0.0;
                });
                break;
            default:
                cmp = Comparator.comparing(User::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        return asc ? cmp : cmp.reversed();
    }

    // ── Batched lookups ──────────────────────────────────

    private Map<String, Cohort> loadCohortsById(List<User> users) {
        Set<String> ids = users.stream().map(User::getCohortId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return cohortRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity()));
    }

    private Map<String, Device> loadDevicesByStudent(List<User> users) {
        Set<String> ids = users.stream().map(User::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return deviceRepository.findByStudentIdIn(ids).stream()
                .collect(Collectors.toMap(Device::getStudentId, Function.identity(), (a, b) -> a));
    }

    private Map<String, UserDto.UserResponse.AttendanceSummary> loadSummariesByStudent(List<User> users) {
        Set<String> ids = users.stream().map(User::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<Attendance> all = attendanceRepository.findByStudentIdIn(ids);
        Map<String, UserDto.UserResponse.AttendanceSummary> out = new HashMap<>();
        all.stream().collect(Collectors.groupingBy(Attendance::getStudentId)).forEach((id, att) -> {
            int present = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int absent = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int excused = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            double rate = att.size() > 0 ? (double) (present + late) / att.size() * 100 : 0;
            out.put(id, new UserDto.UserResponse.AttendanceSummary(att.size(), present, late, absent, excused, rate));
        });
        return out;
    }

    /** Batched response builder — zero per-student queries. */
    private UserDto.UserResponse toResponse(User user,
                                            Map<String, Cohort> cohortsById,
                                            Map<String, Device> devicesByStudent,
                                            Map<String, UserDto.UserResponse.AttendanceSummary> summariesByStudent) {
        UserDto.UserResponse resp = new UserDto.UserResponse();
        resp.setId(user.getId());
        resp.setName(user.getName());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setRole(user.getRole().name());
        resp.setCohortId(user.getCohortId());
        Cohort cohort = user.getCohortId() != null ? cohortsById.get(user.getCohortId()) : null;
        resp.setCohortName(cohort != null ? cohort.getName() : null);
        resp.setRegistrationNumber(user.getRegistrationNumber());
        resp.setAssignedCohortIds(user.getAssignedCohortIds());
        resp.setActive(user.isActive());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setBiometricRegistered(user.getWebAuthnCredentialId() != null && !user.getWebAuthnCredentialId().isEmpty());

        Device device = devicesByStudent.get(user.getId());
        if (device != null) {
            resp.setDevice(new UserDto.UserResponse.DeviceInfo(
                    device.getId(), device.getFingerprint(), device.isLocked(), device.getRegisteredAt()));
        }

        if (user.getRole() == User.Role.STUDENT) {
            UserDto.UserResponse.AttendanceSummary s = summariesByStudent.get(user.getId());
            if (s == null) s = new UserDto.UserResponse.AttendanceSummary(0, 0, 0, 0, 0, 0.0);
            resp.setAttendanceSummary(s);
        }
        return resp;
    }

    public List<UserDto.UserResponse> getStudentsByCohort(String cohortId) {
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        if (students.isEmpty()) return List.of();
        Map<String, Cohort> cohortsById = loadCohortsById(students);
        Map<String, Device> devicesByStudent = loadDevicesByStudent(students);
        Map<String, UserDto.UserResponse.AttendanceSummary> summariesByStudent = loadSummariesByStudent(students);
        return students.stream()
                .map(u -> toResponse(u, cohortsById, devicesByStudent, summariesByStudent))
                .collect(Collectors.toList());
    }

    public UserDto.UserResponse getById(String id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("User not found")));
    }

    public UserDto.UserResponse updateUser(String actorId, String actorName, String actorRole,
                                            String userId, UserDto.UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));
        if (request.getName() != null) user.setName(request.getName());
        if (request.getCohortId() != null) user.setCohortId(request.getCohortId());
        if (request.getRegistrationNumber() != null) user.setRegistrationNumber(request.getRegistrationNumber());
        if (request.getAssignedCohortIds() != null) user.setAssignedCohortIds(request.getAssignedCohortIds());
        if (request.getActive() != null) user.setActive(request.getActive());
        User saved = userRepository.save(user);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.USER_UPDATED, userId, user.getName(), "User profile updated", null);
        return toResponse(saved);
    }

    // ── Device Management ────────────────────────────────
    public UserDto.UserResponse.DeviceInfo registerDevice(String actorId, String actorName,
                                                           String studentId, String fingerprint,
                                                           String userAgent) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));

        Device device = deviceRepository.findByStudentId(studentId).orElse(new Device());
        device.setStudentId(studentId);
        device.setFingerprint(fingerprint);
        device.setUserAgent(userAgent);
        device.setLocked(true);
        device.setRegisteredBy(actorId);
        Device saved = deviceRepository.save(device);

        student.setDeviceId(saved.getId());
        userRepository.save(student);

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.DEVICE_REGISTERED, studentId, student.getName(),
                "Device registered for " + student.getName(), null);

        return new UserDto.UserResponse.DeviceInfo(
                saved.getId(), saved.getFingerprint(), saved.isLocked(), saved.getRegisteredAt());
    }

    public void unlockDevice(String actorId, String actorName, String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));

        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        if (device != null) {
            device.setLocked(false);
            device.setFingerprint(null);
            device.setUserAgent(null);
            device.setRegisteredBy(actorId);
            deviceRepository.save(device);
        }

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.DEVICE_UNLOCKED, studentId, student.getName(),
                "Device reset/cleared — student will bind new device on next scan", null);
    }

    // ── Helpers ──────────────────────────────────────────
    public UserDto.UserResponse toResponse(User user) {
        return toResponse(user, false);
    }

    public UserDto.UserResponse toResponse(User user, boolean includeAnalytics) {
        UserDto.UserResponse resp = new UserDto.UserResponse();
        resp.setId(user.getId());
        resp.setName(user.getName());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setRole(user.getRole().name());
        resp.setCohortId(user.getCohortId());
        resp.setCohortName(user.getCohortId() != null
                ? cohortRepository.findById(user.getCohortId()).map(Cohort::getName).orElse(null) : null);
        resp.setRegistrationNumber(user.getRegistrationNumber());
        resp.setAssignedCohortIds(user.getAssignedCohortIds());
        resp.setActive(user.isActive());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setBiometricRegistered(user.getWebAuthnCredentialId() != null && !user.getWebAuthnCredentialId().isEmpty());

        // Device info
        deviceRepository.findByStudentId(user.getId()).ifPresent(d ->
                resp.setDevice(new UserDto.UserResponse.DeviceInfo(
                        d.getId(), d.getFingerprint(), d.isLocked(), d.getRegisteredAt()))
        );

        // Attendance summary
        if (user.getRole() == User.Role.STUDENT) {
            List<Attendance> att = attendanceRepository.findByStudentId(user.getId());
            int present = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int absent = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int excused = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            double rate = att.size() > 0 ? (double)(present + late) / att.size() * 100 : 0;
            resp.setAttendanceSummary(new UserDto.UserResponse.AttendanceSummary(
                    att.size(), present, late, absent, excused, rate));
            if (includeAnalytics) {
                resp.setAnalytics(attendanceService.buildStudentAnalytics(user.getId()));
            }
        }
        return resp;
    }

    // ── Network & System Settings ───────────────────────
    private static final String[] NETWORK_KEYS = {
        "school_name", "school_address", "school_email", "school_website",
        "school_wifi_ssid", "school_ip_range", "network_enforce",
        "qr_window_start", "qr_window_end", "late_threshold",
        "school_latitude", "school_longitude", "school_geofence_radius_meters", "geofence_fallback_enabled"
    };
    private static final String[] NETWORK_DEFAULTS = {
        "Tech School", "Lagos, Nigeria", "admin@techschool.edu.ng", "https://techschool.edu.ng",
        "TechSchool-WiFi", "192.168.1.0/24", "false",
        "07:00", "12:00", "08:31",
        "6.5244", "3.3792", "150", "true"
    };

    public Map<String, String> getNetworkSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        for (int i = 0; i < NETWORK_KEYS.length; i++) {
            SystemSetting setting = systemSettingRepository.findByKey(NETWORK_KEYS[i]).orElse(null);
            settings.put(NETWORK_KEYS[i], setting != null ? setting.getValue() : NETWORK_DEFAULTS[i]);
        }
        return settings;
    }

    public Map<String, String> updateNetworkSettings(String actorId, String actorName, Map<String, String> updates) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            SystemSetting setting = systemSettingRepository.findByKey(key).orElse(new SystemSetting());
            setting.setKey(key);
            setting.setValue(value);
            systemSettingRepository.save(setting);
            result.put(key, value);
        }
        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.USER_UPDATED, actorId, actorName,
                "Network settings updated: " + String.join(", ", updates.keySet()), null);
        return result;
    }
}
