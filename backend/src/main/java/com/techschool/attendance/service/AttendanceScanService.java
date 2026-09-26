package com.techschool.attendance.service;

import com.techschool.attendance.data.model.*;
import com.techschool.attendance.data.repository.*;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceScanService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final QrService qrService;
    private final AuditService auditService;
    private final HolidayService holidayService;
    private final AttendanceCheckInService attendanceCheckInService;
    private final AttendanceDeviceService attendanceDeviceService;

    @Value("${app.attendance.late-threshold}")
    private String lateThreshold;

    @Value("${app.attendance.qr-window-start:07:00}")
    private String windowStartDefault;

    @Value("${app.attendance.qr-window-end:12:00}")
    private String windowEndDefault;

    @Value("${app.attendance.timezone}")
    private String timezone;

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

        if (attendanceRepository.existsByStudentIdAndDate(studentId, today)) {
            throw AppException.conflict("Attendance already marked for today");
        }

        QrSession session = qrService.validateToken(request.getToken());

        if (!session.getCohortId().equals(student.getCohortId())) {
            throw AppException.forbidden("This QR code is not for your cohort");
        }

        String verificationMethod = attendanceCheckInService.verifyCheckIn(request, studentId, ipAddress);

        Device device = attendanceDeviceService.registerOrVerifyScanDevice(studentId, request);

        Attendance.AttendanceStatus status = determineStatus(student.getCohortId());

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

        qrService.incrementScanCount(session.getId());

        auditService.log(studentId, student.getName(), "STUDENT",
                AuditLog.ActionType.ATTENDANCE_MARKED,
                attendance.getId(), student.getName(),
                status + " (" + verificationMethod + ") — " + getCohortName(student.getCohortId()), ipAddress);

        return new QrResponseDto.ScanResponse(true,
                "Attendance marked: " + status.name().toLowerCase(),
                status, attendance.getMarkedAt(), verificationMethod);
    }

    private String getSetting(String key, String defaultVal) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .orElse(defaultVal);
    }

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
}
