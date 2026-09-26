package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.Device;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.UserResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final AttendanceService attendanceService;
    private final MongoTemplate mongoTemplate;

    public List<UserResponseDto.UserResponse> getUsersByRole(User.Role role) {
        List<User> users = userRepository.findByRole(role);
        return toResponses(users);
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchStudents(
            String cohortId, String query, int page, int size) {
        return searchStudents(cohortId, query, page, size, "name", "asc");
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchStudents(
            String cohortId, String query, int page, int size, String sort, String order) {
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        Criteria criteria = Criteria.where("role").is(User.Role.STUDENT);
        if (cohortId != null && !cohortId.isBlank()) criteria.and("cohortId").is(cohortId.trim());
        if (query != null && !query.trim().isEmpty()) {
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(query.trim()), java.util.regex.Pattern.CASE_INSENSITIVE);
            criteria.andOperator(new Criteria().orOperator(Criteria.where("name").regex(regex),
                    Criteria.where("email").regex(regex), Criteria.where("registrationNumber").regex(regex)));
        }

        long total = mongoTemplate.count(Query.query(criteria), User.class);
        boolean ascending = !"desc".equalsIgnoreCase(order);
        String sortProperty = "name";
        if ("email".equalsIgnoreCase(sort)) sortProperty = "email";
        else if ("registrationnumber".equalsIgnoreCase(sort) || "registration".equalsIgnoreCase(sort)) {
            sortProperty = "registrationNumber";
        }
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, sortProperty));
        List<User> users = mongoTemplate.find(Query.query(criteria).with(pageable), User.class);
        List<UserResponseDto.UserResponse> content = toResponses(users);
        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, (int) total,
                (int) Math.ceil((double) total / safeSize));
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchDevices(
            String query, int page, int size, String sort, String order) {
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        Criteria criteria = Criteria.where("role").is(User.Role.STUDENT);
        if (query != null && !query.trim().isEmpty()) {
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(query.trim()), java.util.regex.Pattern.CASE_INSENSITIVE);
            criteria.andOperator(new Criteria().orOperator(Criteria.where("name").regex(regex),
                    Criteria.where("email").regex(regex)));
        }
        long total = mongoTemplate.count(Query.query(criteria), User.class);
        boolean ascending = !"desc".equalsIgnoreCase(order);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "name"));
        List<User> users = mongoTemplate.find(Query.query(criteria).with(pageable), User.class);
        return new AnalyticsResponseDto.PageResponse<>(toResponses(users), safePage, safeSize, (int) total,
                (int) Math.ceil((double) total / safeSize));
    }

    public List<UserResponseDto.UserResponse> getStudentsByCohort(String cohortId) {
        return toResponses(userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT));
    }

    public UserResponseDto.UserResponse getById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> AppException.notFound("User not found"));
        return toResponse(user);
    }

    public UserResponseDto.UserResponse toResponse(User user) {
        return toResponse(user, false);
    }

    public UserResponseDto.UserResponse toResponse(User user, boolean includeAnalytics) {
        UserResponseDto.UserResponse response = new UserResponseDto.UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole().name());
        response.setCohortId(user.getCohortId());
        response.setCohortName(user.getCohortId() != null
                ? cohortRepository.findById(user.getCohortId()).map(Cohort::getName).orElse(null) : null);
        response.setRegistrationNumber(user.getRegistrationNumber());
        response.setAssignedCohortIds(user.getAssignedCohortIds());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setBiometricRegistered(user.getWebAuthnCredentialId() != null
                && !user.getWebAuthnCredentialId().isEmpty());

        deviceRepository.findByStudentId(user.getId()).ifPresent(device ->
                response.setDevice(new UserResponseDto.UserResponse.DeviceInfo(
                        device.getId(), device.getFingerprint(), device.isLocked(), device.getRegisteredAt())));
        if (user.getRole() == User.Role.STUDENT) {
            AnalyticsResponseDto.StudentAnalytics analytics = attendanceService.buildStudentAnalytics(user.getId());
            response.setAttendanceSummary(new UserResponseDto.UserResponse.AttendanceSummary(
                    analytics.getTotalRecords(), analytics.getPresent(), analytics.getLate(),
                    analytics.getAbsent(), analytics.getExcused(), analytics.getAttendanceRate()));
            if (includeAnalytics) response.setAnalytics(analytics);
        }
        return response;
    }

    private List<UserResponseDto.UserResponse> toResponses(List<User> users) {
        if (users.isEmpty()) return List.of();
        Map<String, Cohort> cohorts = loadCohortsById(users);
        Map<String, Device> devices = loadDevicesByStudent(users);
        Map<String, UserResponseDto.UserResponse.AttendanceSummary> summaries =
                users.stream().anyMatch(user -> user.getRole() == User.Role.STUDENT)
                        ? loadSummariesByStudent(users) : Map.of();
        return users.stream().map(user -> toResponse(user, cohorts, devices, summaries)).collect(Collectors.toList());
    }

    private Map<String, Cohort> loadCohortsById(List<User> users) {
        Set<String> ids = users.stream().map(User::getCohortId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return cohortRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (first, ignored) -> first));
    }

    private Map<String, Device> loadDevicesByStudent(List<User> users) {
        Set<String> ids = users.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return deviceRepository.findByStudentIdIn(ids).stream()
                .collect(Collectors.toMap(Device::getStudentId, Function.identity(), (first, ignored) -> first));
    }

    private Map<String, UserResponseDto.UserResponse.AttendanceSummary> loadSummariesByStudent(List<User> users) {
        Set<String> ids = users.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        List<Attendance> attendance = attendanceRepository.findByStudentIdIn(ids);
        Map<String, UserResponseDto.UserResponse.AttendanceSummary> summaries = new HashMap<>();
        attendance.stream().collect(Collectors.groupingBy(Attendance::getStudentId)).forEach((studentId, records) -> {
            List<Attendance> weekdayRecords = records.stream()
                    .filter(record -> record.getDate() != null && record.getDate().getDayOfWeek().getValue() < 6)
                    .collect(Collectors.toList());
            int present = count(weekdayRecords, Attendance.AttendanceStatus.PRESENT);
            int late = count(weekdayRecords, Attendance.AttendanceStatus.LATE);
            int absent = count(weekdayRecords, Attendance.AttendanceStatus.ABSENT);
            int excused = count(weekdayRecords, Attendance.AttendanceStatus.EXCUSED);
            double rate = weekdayRecords.isEmpty() ? 0.0 : (double) (present + late) / weekdayRecords.size() * 100;
            summaries.put(studentId, new UserResponseDto.UserResponse.AttendanceSummary(
                    weekdayRecords.size(), present, late, absent, excused, rate));
        });
        return summaries;
    }

    private int count(List<Attendance> records, Attendance.AttendanceStatus status) {
        return (int) records.stream().filter(record -> record.getStatus() == status).count();
    }

    private UserResponseDto.UserResponse toResponse(User user, Map<String, Cohort> cohorts,
            Map<String, Device> devices,
            Map<String, UserResponseDto.UserResponse.AttendanceSummary> summaries) {
        UserResponseDto.UserResponse response = new UserResponseDto.UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole().name());
        response.setCohortId(user.getCohortId());
        Cohort cohort = user.getCohortId() != null ? cohorts.get(user.getCohortId()) : null;
        response.setCohortName(cohort != null ? cohort.getName() : null);
        response.setRegistrationNumber(user.getRegistrationNumber());
        response.setAssignedCohortIds(user.getAssignedCohortIds());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setBiometricRegistered(user.getWebAuthnCredentialId() != null
                && !user.getWebAuthnCredentialId().isEmpty());
        Device device = devices.get(user.getId());
        if (device != null) {
            response.setDevice(new UserResponseDto.UserResponse.DeviceInfo(
                    device.getId(), device.getFingerprint(), device.isLocked(), device.getRegisteredAt()));
        }
        if (user.getRole() == User.Role.STUDENT) {
            UserResponseDto.UserResponse.AttendanceSummary summary = summaries.get(user.getId());
            if (summary == null) summary = new UserResponseDto.UserResponse.AttendanceSummary(0, 0, 0, 0, 0, 0.0);
            response.setAttendanceSummary(summary);
        }
        return response;
    }
}
