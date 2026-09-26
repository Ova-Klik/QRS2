package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Device;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceDeviceService {

    private final DeviceRepository deviceRepository;

    public Device registerOrVerifyScanDevice(String studentId, QrRequestDto.ScanRequest request) {
        String fingerprint = request.getDeviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            throw AppException.badRequest("Device fingerprint is required to mark attendance.");
        }
        fingerprint = fingerprint.trim();

        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        if (device == null || !device.isLocked() || device.getFingerprint() == null) {
            if (device == null) {
                device = new Device();
                device.setStudentId(studentId);
            }
            device.setFingerprint(fingerprint);
            device.setUserAgent(request.getUserAgent());
            device.setLocked(true);
            device.setRegisteredAt(Instant.now());
            device.setRegisteredBy("AUTO_FIRST_SCAN");
            device = deviceRepository.save(device);
            log.info("Auto-registered first device scan for student {}: {}", studentId, fingerprint);
        } else if (!fingerprint.equals(device.getFingerprint())) {
            log.warn("Device fingerprint mismatch for student {} — expected {} got {}",
                    studentId, device.getFingerprint(), fingerprint);
            throw AppException.forbidden(
                    "Device mismatch. Attendance can only be marked from your registered device. Contact your admin to reset your device.");
        }

        return device;
    }
}
