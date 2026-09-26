package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.request.UserRequestDto;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.UserResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserDirectoryService userDirectoryService;
    private final UserDeviceService userDeviceService;
    private final UserSettingsService userSettingsService;
    private final UserAttendanceReportService userAttendanceReportService;

    public UserResponseDto.UserResponse createUser(String actorId, String actorName, String actorRole,
                                                   UserRequestDto.CreateUserRequest request) {
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

        auditService.log(actorId, actorName, actorRole, AuditLog.ActionType.USER_CREATED,
                saved.getId(), saved.getName(), saved.getRole() + " account created", null);
        return userDirectoryService.toResponse(saved);
    }

    public List<UserResponseDto.UserResponse> getUsersByRole(User.Role role) {
        return userDirectoryService.getUsersByRole(role);
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchStudents(
            String cohortId, String query, int page, int size) {
        return userDirectoryService.searchStudents(cohortId, query, page, size);
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchStudents(
            String cohortId, String query, int page, int size, String sort, String order) {
        return userDirectoryService.searchStudents(cohortId, query, page, size, sort, order);
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.UserResponse> searchDevices(
            String query, int page, int size, String sort, String order) {
        return userDirectoryService.searchDevices(query, page, size, sort, order);
    }

    public AnalyticsResponseDto.PageResponse<UserResponseDto.StudentAttendanceResponse> searchStudentsAdmin(
            String cohortId, String query, LocalDate startDate, LocalDate endDate, String status,
            int page, int size, String sort, String order) {
        return userAttendanceReportService.searchStudentsAdmin(
                cohortId, query, startDate, endDate, status, page, size, sort, order);
    }

    public void deleteStudent(String actorId, String actorName, String actorRole, String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        if (student.getRole() != User.Role.STUDENT) {
            throw AppException.badRequest("User is not a student");
        }

        List<Attendance> attendance = attendanceRepository.findByStudentId(studentId);
        if (!attendance.isEmpty()) attendanceRepository.deleteAll(attendance);
        deviceRepository.findByStudentId(studentId).ifPresent(deviceRepository::delete);
        userRepository.delete(student);

        auditService.log(actorId, actorName, actorRole, AuditLog.ActionType.USER_DELETED,
                studentId, student.getName(), "Student account and associated attendance data deleted", null);
    }

    public ResponseEntity<byte[]> exportStudentsAdmin(String cohortId, String query,
            LocalDate startDate, LocalDate endDate, String status, String format, ExportService exportService) {
        return userAttendanceReportService.exportStudentsAdmin(
                cohortId, query, startDate, endDate, status, format, exportService);
    }

    public List<UserResponseDto.UserResponse> getStudentsByCohort(String cohortId) {
        return userDirectoryService.getStudentsByCohort(cohortId);
    }

    public UserResponseDto.UserResponse getById(String id) {
        return userDirectoryService.getById(id);
    }

    public UserResponseDto.UserResponse updateUser(String actorId, String actorName, String actorRole,
            String userId, UserRequestDto.UpdateUserRequest request) {
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
        if (request.getPhone() != null) user.setPhone(request.getPhone().trim());
        if (request.getCohortId() != null) user.setCohortId(request.getCohortId());
        if (request.getRegistrationNumber() != null) user.setRegistrationNumber(request.getRegistrationNumber());

        if (request.getAssignedCohortIds() != null) {
            user.setAssignedCohortIds(request.getAssignedCohortIds());
            if (user.getRole() == User.Role.FACILITATOR) {
                List<Cohort> cohorts = cohortRepository.findAll();
                for (Cohort cohort : cohorts) {
                    if (request.getAssignedCohortIds().contains(cohort.getId())) {
                        if (!userId.equals(cohort.getFacilitatorId())) {
                            cohort.setFacilitatorId(userId);
                            cohortRepository.save(cohort);
                        }
                    } else if (userId.equals(cohort.getFacilitatorId())) {
                        cohort.setFacilitatorId(null);
                        cohortRepository.save(cohort);
                    }
                }
            }
        }
        if (request.getActive() != null) user.setActive(request.getActive());
        User saved = userRepository.save(user);

        auditService.log(actorId, actorName, actorRole, AuditLog.ActionType.USER_UPDATED,
                userId, user.getName(),
                (user.getRole() == User.Role.FACILITATOR ? "Facilitator" : "User")
                        + " profile updated: " + user.getName(), null);
        return userDirectoryService.toResponse(saved);
    }

    public UserResponseDto.UserResponse.DeviceInfo registerDevice(String actorId, String actorName,
            String studentId, String fingerprint, String userAgent) {
        return userDeviceService.registerDevice(actorId, actorName, studentId, fingerprint, userAgent);
    }

    public void unlockDevice(String actorId, String actorName, String studentId) {
        userDeviceService.unlockDevice(actorId, actorName, studentId);
    }

    public UserResponseDto.UserResponse toResponse(User user) {
        return userDirectoryService.toResponse(user);
    }

    public UserResponseDto.UserResponse toResponse(User user, boolean includeAnalytics) {
        return userDirectoryService.toResponse(user, includeAnalytics);
    }

    public Map<String, String> getNetworkSettings() {
        return userSettingsService.getNetworkSettings();
    }

    public Map<String, String> updateNetworkSettings(String actorId, String actorName, Map<String, String> updates) {
        return userSettingsService.updateNetworkSettings(actorId, actorName, updates);
    }
}
