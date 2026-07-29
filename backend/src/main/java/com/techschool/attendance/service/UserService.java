package com.techschool.attendance.service;

import com.techschool.attendance.dto.UserDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        user.setAssignedCohortIds(request.getAssignedCohortIds());
        user.setActive(true);
        User saved = userRepository.save(user);

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.USER_CREATED, saved.getId(), saved.getName(),
                saved.getRole() + " account created", null);

        return toResponse(saved);
    }

    public List<UserDto.UserResponse> getUsersByRole(User.Role role) {
        return userRepository.findByRole(role).stream()
                .map(this::toResponse).collect(Collectors.toList());
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
        UserDto.UserResponse resp = new UserDto.UserResponse();
        resp.setId(user.getId());
        resp.setName(user.getName());
        resp.setEmail(user.getEmail());
        resp.setPhone(user.getPhone());
        resp.setRole(user.getRole().name());
        resp.setCohortId(user.getCohortId());
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
