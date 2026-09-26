package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.ExcuseRequest;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.ExcuseRequestRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceManualAttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final ExcuseRequestRepository excuseRepository;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.ManualStudentAttendanceResponse> getManualAttendancePage(
            List<String> assignedCohortIds, String cohortId, String queryStr, LocalDate targetDate, int page, int size) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank()) ? List.of(cohortId) : assignedCohortIds;

        if (targetCohortIds.isEmpty()) {
            return new AnalyticsResponseDto.PageResponse<>(List.of(), page, size, 0, 1);
        }

        List<User> students = userRepository.findByCohortIdIn(targetCohortIds);
        String query = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!query.isEmpty()) {
            students = students.stream().filter(student ->
                    (student.getName() != null && student.getName().toLowerCase().contains(query)) ||
                    (student.getEmail() != null && student.getEmail().toLowerCase().contains(query)) ||
                    (student.getRegistrationNumber() != null && student.getRegistrationNumber().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        int total = students.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<User> pagedStudents = students.subList(from, to);
        Set<String> pagedStudentIds = pagedStudents.stream().map(User::getId).collect(Collectors.toSet());

        List<Attendance> existingAttendance = pagedStudentIds.isEmpty() ? List.of()
                : attendanceRepository.findByStudentIdIn(pagedStudentIds).stream()
                    .filter(record -> date.equals(record.getDate()))
                    .collect(Collectors.toList());
        Map<String, Attendance> attendanceByStudent = existingAttendance.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, record -> record, (first, ignored) -> first));

        List<ExcuseRequest> excuses = pagedStudentIds.isEmpty() ? List.of()
                : excuseRepository.findByStudentIdIn(pagedStudentIds).stream()
                    .filter(excuse -> excuse.getStatus() == ExcuseRequest.Status.ACCEPTED || excuse.getStatus() == ExcuseRequest.Status.APPROVED)
                    .filter(excuse -> excuse.getStartDate() != null && !date.isBefore(excuse.getStartDate())
                            && !date.isAfter(excuse.getStartDate().plusDays(Math.max(1, excuse.getNumberOfDays()) - 1)))
                    .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, excuse -> excuse, (first, ignored) -> first));
        Map<String, Cohort> cohortsById = cohortRepository.findAllById(targetCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, cohort -> cohort, (first, ignored) -> first));

        List<AttendanceResponseDto.ManualStudentAttendanceResponse> content = pagedStudents.stream().map(student -> {
            Cohort cohort = student.getCohortId() != null ? cohortsById.get(student.getCohortId()) : null;
            Attendance attendance = attendanceByStudent.get(student.getId());
            ExcuseRequest excuse = excuseByStudent.get(student.getId());

            String status;
            Instant markedAt = null;
            boolean manual = false;
            String manualReason = null;

            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            if (weekend) {
                status = attendance != null && attendance.getStatus() != null
                        && attendance.getStatus() != Attendance.AttendanceStatus.ABSENT
                        ? attendance.getStatus().name() : "WEEKEND";
                markedAt = attendance != null ? attendance.getMarkedAt() : null;
                manual = attendance != null && attendance.isManual();
                manualReason = attendance != null ? attendance.getManualReason() : null;
            } else if (attendance != null) {
                status = attendance.getStatus() != null ? attendance.getStatus().name() : "ABSENT";
                markedAt = attendance.getMarkedAt();
                manual = attendance.isManual();
                manualReason = attendance.getManualReason();
            } else if (excuse != null) {
                status = "EXCUSED";
                manualReason = "Approved excuse: " + excuse.getReason();
            } else {
                status = "ABSENT";
            }

            return new AttendanceResponseDto.ManualStudentAttendanceResponse(
                    student.getId(), student.getName(), student.getRegistrationNumber(), student.getEmail(),
                    student.getCohortId(), cohort != null ? cohort.getName() : student.getCohortId(),
                    date, status, markedAt, manual, manualReason);
        }).collect(Collectors.toList());

        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }
}
