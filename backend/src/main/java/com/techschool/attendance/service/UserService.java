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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final ExcuseRequestRepository excuseRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AttendanceService attendanceService;
    private final HolidayService holidayService;

    @org.springframework.beans.factory.annotation.Value("${app.attendance.timezone:Africa/Lagos}")
    private String timezone;

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

        final LocalDate effStart = startDate;
        final LocalDate effEnd = endDate;
        final LocalDate targetDate = effStart != null ? effStart : LocalDate.now(ZoneId.of(timezone));

        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr.trim())) {
            String s = statusStr.trim().toUpperCase();
            Set<String> matchingIds = new HashSet<>();

            List<Attendance> dateAtt = attendanceRepository.findByDate(targetDate);
            Map<String, Attendance> attByStudent = dateAtt.stream()
                    .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

            List<ExcuseRequest> excuses = excuseRepository.findAll().stream()
                    .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                    .filter(e -> e.getStartDate() != null && !targetDate.isBefore(e.getStartDate()) && !targetDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                    .collect(Collectors.toList());
            Set<String> excusedStudentIds = excuses.stream().map(ExcuseRequest::getStudentId).collect(Collectors.toSet());

            for (User u : candidates) {
                Attendance a = attByStudent.get(u.getId());
                boolean hasExcuse = excusedStudentIds.contains(u.getId()) || (a != null && a.getStatus() == Attendance.AttendanceStatus.EXCUSED);

                if ("PRESENT".equals(s)) {
                    if (a != null && (a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.LATE)) {
                        matchingIds.add(u.getId());
                    }
                } else if ("EARLY".equals(s)) {
                    if (a != null && a.getStatus() == Attendance.AttendanceStatus.PRESENT) {
                        matchingIds.add(u.getId());
                    }
                } else if ("LATE".equals(s)) {
                    if (a != null && a.getStatus() == Attendance.AttendanceStatus.LATE) {
                        matchingIds.add(u.getId());
                    }
                } else if ("EXCUSED".equals(s)) {
                    if (hasExcuse) {
                        matchingIds.add(u.getId());
                    }
                } else if ("ABSENT".equals(s)) {
                    if (a == null && !hasExcuse) {
                        matchingIds.add(u.getId());
                    }
                }
            }

            filtered = filtered.stream()
                    .filter(u -> matchingIds.contains(u.getId()))
                    .collect(Collectors.toList());
        }

        int total = filtered.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        boolean sortByRate = "rate".equalsIgnoreCase(sort) || "attendancerate".equalsIgnoreCase(sort) || "attendance".equalsIgnoreCase(sort);

        List<User> pageUsers;
        Map<String, UserDto.StudentAttendanceResponse> responsesByStudent;

        if (sortByRate) {
            Map<String, Cohort> cohortsById = loadCohortsById(filtered);
            responsesByStudent = loadStudentAttendanceResponses(filtered, effStart, effEnd, cohortsById);
            boolean asc = !"desc".equalsIgnoreCase(order);
            filtered.sort((u1, u2) -> {
                UserDto.StudentAttendanceResponse r1 = responsesByStudent.get(u1.getId());
                UserDto.StudentAttendanceResponse r2 = responsesByStudent.get(u2.getId());
                double rate1 = r1 != null ? r1.getAttendanceRate() : 0.0;
                double rate2 = r2 != null ? r2.getAttendanceRate() : 0.0;
                return asc ? Double.compare(rate1, rate2) : Double.compare(rate2, rate1);
            });
            pageUsers = filtered.subList(from, to);
        } else {
            // Fast path: Sort candidate users first, then compute attendance stats ONLY for the paged slice
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
                default:
                    cmp = Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            }
            filtered.sort(asc ? cmp : cmp.reversed());

            pageUsers = filtered.subList(from, to);
            Map<String, Cohort> cohortsById = loadCohortsById(pageUsers);
            responsesByStudent = loadStudentAttendanceResponses(pageUsers, effStart, effEnd, cohortsById);
        }

        List<UserDto.StudentAttendanceResponse> content = pageUsers.stream()
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

        List<ExcuseRequest> excuses = excuseRepository.findByStudentIdIn(ids).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .collect(Collectors.toList());
        Map<String, List<ExcuseRequest>> excusesByStudent = excuses.stream()
                .collect(Collectors.groupingBy(ExcuseRequest::getStudentId));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        Map<String, UserDto.StudentAttendanceResponse> out = new HashMap<>();
        for (User u : students) {
            List<Attendance> att = byStudent.getOrDefault(u.getId(), List.of());
            List<ExcuseRequest> stExcuses = excusesByStudent.getOrDefault(u.getId(), List.of());

            LocalDate effStart = startDate;
            LocalDate effEnd = endDate;
            if (effStart == null || effEnd == null) {
                LocalDate creationDate = u.getCreatedAt() != null
                        ? ZonedDateTime.ofInstant(u.getCreatedAt(), ZoneId.of(timezone)).toLocalDate() : today;
                LocalDate earliestAtt = att.stream().map(Attendance::getDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(creationDate);
                effStart = creationDate.isBefore(earliestAtt) ? creationDate : earliestAtt;
                if (effStart.isAfter(today)) effStart = today;
                effEnd = today;
            }

            Set<LocalDate> holidays = holidayService.holidayDatesBetween(effStart, effEnd, u.getCohortId());
            Map<LocalDate, Attendance> attMap = att.stream()
                    .collect(Collectors.toMap(Attendance::getDate, Function.identity(), (a, b) -> a));

            int present = 0, late = 0, absent = 0, excused = 0, holiday = 0, totalDays = 0;
            for (LocalDate d = effStart; !d.isAfter(effEnd); d = d.plusDays(1)) {
                if (!holidayService.isSchoolDay(d, holidays)) continue;
                totalDays++;
                final LocalDate currDate = d;
                Attendance a = attMap.get(d);
                boolean isExcused = stExcuses.stream().anyMatch(e -> e.getStartDate() != null &&
                        !currDate.isBefore(e.getStartDate()) && !currDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)));

                if (a != null) {
                    if (a.getStatus() == Attendance.AttendanceStatus.PRESENT) present++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.LATE) late++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.EXCUSED) excused++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.HOLIDAY) holiday++;
                    else absent++;
                } else if (isExcused) {
                    excused++;
                } else {
                    absent++;
                }
            }

            int attended = present + late;
            double rate = (totalDays - excused) > 0 ? (double) attended / (totalDays - excused) * 100.0
                    : (attended > 0 ? 100.0 : 0.0);
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
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));
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
            List<Attendance> validAtt = att.stream()
                    .filter(a -> a.getDate() != null && a.getDate().getDayOfWeek().getValue() < 6)
                    .collect(Collectors.toList());
            int present = (int) validAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) validAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int absent = (int) validAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int excused = (int) validAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            double rate = validAtt.size() > 0 ? (double) (present + late) / validAtt.size() * 100 : 0;
            out.put(id, new UserDto.UserResponse.AttendanceSummary(validAtt.size(), present, late, absent, excused, rate));
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

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(newEmail).ifPresent(existing -> {
                    if (!existing.getId().equals(userId)) {
                        throw AppException.badRequest("Email address is already in use");
                    }
                });
                user.setEmail(newEmail);
            }
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        if (request.getCohortId() != null) user.setCohortId(request.getCohortId());
        if (request.getRegistrationNumber() != null) user.setRegistrationNumber(request.getRegistrationNumber());

        if (request.getAssignedCohortIds() != null) {
            user.setAssignedCohortIds(request.getAssignedCohortIds());
            if (user.getRole() == User.Role.FACILITATOR) {
                // Sync cohort facilitator assignment
                List<Cohort> cohorts = cohortRepository.findAll();
                for (Cohort c : cohorts) {
                    if (request.getAssignedCohortIds().contains(c.getId())) {
                        if (!userId.equals(c.getFacilitatorId())) {
                            c.setFacilitatorId(userId);
                            cohortRepository.save(c);
                        }
                    } else if (userId.equals(c.getFacilitatorId())) {
                        c.setFacilitatorId(null);
                        cohortRepository.save(c);
                    }
                }
            }
        }

        if (request.getActive() != null) user.setActive(request.getActive());
        User saved = userRepository.save(user);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.USER_UPDATED, userId, user.getName(),
                (user.getRole() == User.Role.FACILITATOR ? "Facilitator" : "User") + " profile updated: " + user.getName(), null);
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
            AnalyticsDto.StudentAnalytics analytics = attendanceService.buildStudentAnalytics(user.getId());
            resp.setAttendanceSummary(new UserDto.UserResponse.AttendanceSummary(
                    analytics.getTotalRecords(),
                    analytics.getPresent(),
                    analytics.getLate(),
                    analytics.getAbsent(),
                    analytics.getExcused(),
                    analytics.getAttendanceRate()));
            if (includeAnalytics) {
                resp.setAnalytics(analytics);
            }
        }
        return resp;
    }

    // ── Network & System Settings ───────────────────────
    private static final String[] NETWORK_KEYS = {
        "school_name", "school_address", "school_email", "school_website",
        "school_wifi_ssid", "school_ip_range", "network_enforce",
        "qr_window_start", "qr_window_end", "late_threshold",
        "school_latitude", "school_longitude", "school_geofence_radius_meters", "geofence_enforce", "geofence_fallback_enabled",
        "qr_refresh_interval", "qr_refresh_enabled"
    };
    private static final String[] NETWORK_DEFAULTS = {
        "Tech School", "Lagos, Nigeria", "admin@techschool.edu.ng", "https://techschool.edu.ng",
        "TechSchool-WiFi", "192.168.1.0/24", "false",
        "07:00", "12:00", "08:31",
        "6.5244", "3.3792", "150", "false", "true",
        "15", "true"
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
        if (updates.containsKey("qr_refresh_interval")) {
            String val = updates.get("qr_refresh_interval");
            try {
                int interval = Integer.parseInt(val != null ? val.trim() : "");
                if (interval < 5 || interval > 600) {
                    throw AppException.badRequest("QR Refresh Interval must be between 5 and 600 seconds.");
                }
            } catch (NumberFormatException e) {
                throw AppException.badRequest("QR Refresh Interval must be a valid integer between 5 and 600 seconds.");
            }
        }

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
