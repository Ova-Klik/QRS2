package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.request.AttendanceRequestDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AttendanceMarkingService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AttendanceAnalyticsService attendanceAnalyticsService;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public AttendanceResponseDto.AttendanceRecord markManual(
            String actorId,
            String actorName,
            String actorRole,
            AttendanceRequestDto.ManualMarkRequest request,
            String ipAddress) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> AppException.notFound("Student not found"));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));
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

        return attendanceAnalyticsService.toRecord(saved);
    }
}
