package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Device;
import com.techschool.attendance.data.model.ExcuseRequest;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.DeviceRepository;
import com.techschool.attendance.data.repository.ExcuseRequestRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.dto.response.DashboardResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohortStudentDashboardStatsService {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final DeviceRepository deviceRepository;
    private final ExcuseRequestRepository excuseRepository;
    private final HolidayService holidayService;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public DashboardResponseDto.StudentStats buildStudentStats(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        List<Attendance> all = attendanceRepository.findByStudentIdOrderByDateAsc(studentId);
        Map<LocalDate, Attendance.AttendanceStatus> statusByDate = all.stream()
                .collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a, LinkedHashMap::new));
        List<ExcuseRequest> excuses = excuseRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .collect(Collectors.toList());

        LocalDate creationDate = student.getCreatedAt() != null
                ? ZonedDateTime.ofInstant(student.getCreatedAt(), ZoneId.of(timezone)).toLocalDate() : today;
        LocalDate earliestAttDate = all.isEmpty() ? creationDate : all.get(0).getDate();
        LocalDate startDate = creationDate.isBefore(earliestAttDate) ? creationDate : earliestAttDate;
        if (startDate.isAfter(today)) startDate = today;
        Set<LocalDate> holidays = holidayService.holidayDatesBetween(startDate, today, cohortId);

        int totalDays = 0, present = 0, late = 0, excused = 0, absent = 0;
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            if (!holidayService.isSchoolDay(d, holidays)) continue;
            totalDays++;
            final LocalDate currDate = d;
            Attendance.AttendanceStatus st = statusByDate.get(d);
            boolean isExcused = excuses.stream().anyMatch(e -> e.getStartDate() != null
                    && !currDate.isBefore(e.getStartDate())
                    && !currDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)));
            if (st == Attendance.AttendanceStatus.PRESENT) present++;
            else if (st == Attendance.AttendanceStatus.LATE) late++;
            else if (st == Attendance.AttendanceStatus.EXCUSED || isExcused) excused++;
            else if (st == Attendance.AttendanceStatus.HOLIDAY) totalDays--;
            else absent++;
        }

        int attended = present + late;
        double rate = (totalDays - excused) > 0 ? (double) attended / (totalDays - excused) * 100.0
                : (attended > 0 ? 100.0 : 0.0);
        rate = Math.round(rate * 10.0) / 10.0;
        java.util.Optional<Attendance> todayRecord = attendanceRepository.findByStudentIdAndDate(studentId, today);
        boolean markedToday = todayRecord.isPresent();
        String todayStatus = markedToday ? todayRecord.get().getStatus().name() : null;
        List<AttendanceResponseDto.AttendanceRecord> recent = all.stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate())).limit(10)
                .map(a -> new AttendanceResponseDto.AttendanceRecord(
                        a.getId(), a.getStudentId(), student.getName(), student.getRegistrationNumber(),
                        a.getCohortId(), null, a.getDate(), a.getMarkedAt(),
                        a.getStatus() != null ? a.getStatus().name() : null, a.isManual(), a.getManualReason(), null))
                .collect(Collectors.toList());

        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        DashboardResponseDto.StudentStats.DeviceStatus deviceStatus = device != null
                ? new DashboardResponseDto.StudentStats.DeviceStatus(device.isLocked(), device.getFingerprint(), device.getRegisteredAt())
                : new DashboardResponseDto.StudentStats.DeviceStatus(false, null, null);
        return new DashboardResponseDto.StudentStats(
                totalDays, present, late, absent, excused, rate,
                markedToday, todayStatus, recent, deviceStatus);
    }
}