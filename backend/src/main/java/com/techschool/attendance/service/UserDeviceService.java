package com.techschool.attendance.service;

import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.Device;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.UserResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDeviceService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;

    public UserResponseDto.UserResponse.DeviceInfo registerDevice(
            String actorId, String actorName, String studentId, String fingerprint, String userAgent) {
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
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.DEVICE_REGISTERED,
                studentId, student.getName(), "Device registered for " + student.getName(), null);

        return new UserResponseDto.UserResponse.DeviceInfo(
                saved.getId(), saved.getFingerprint(), saved.isLocked(), saved.getRegisteredAt());
    }

    public void unlockDevice(String actorId, String actorName, String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        deviceRepository.findByStudentId(studentId).ifPresent(device -> {
            device.setLocked(false);
            device.setFingerprint(null);
            device.setUserAgent(null);
            device.setRegisteredBy(actorId);
            deviceRepository.save(device);
        });
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.DEVICE_UNLOCKED,
                studentId, student.getName(),
                "Device reset/cleared — student will bind new device on next scan", null);
    }
}
