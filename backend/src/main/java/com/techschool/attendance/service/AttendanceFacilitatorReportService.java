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
public class AttendanceFacilitatorReportService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final ExcuseRequestRepository excuseRepository;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr,
            LocalDate targetDate, int page, int size) {
        return getFacilitatorReportPage(assignedCohortIds, cohortId, queryStr, targetDate, null, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportPage(
            List<String> assignedCohortIds, String cohortId, String queryStr,
            LocalDate targetDate, String statusFilter, int page, int size) {
        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank())
                ? List.of(cohortId) : assignedCohortIds;
        if (targetCohortIds == null || targetCohortIds.isEmpty()) {
            return new AnalyticsResponseDto.PageResponse<>(List.of(), page, size, 0, 1);
        }

        List<AttendanceResponseDto.AttendanceRecord> records = getFacilitatorReportRecords(
                assignedCohortIds, cohortId, queryStr, targetDate, statusFilter);
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, records.size());
        int to = Math.min(from + safeSize, records.size());
        List<AttendanceResponseDto.AttendanceRecord> content = records.subList(from, to);

        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, records.size(),
                (int) Math.ceil((double) records.size() / safeSize));
    }

    public List<AttendanceResponseDto.AttendanceRecord> getFacilitatorReportRecords(
            List<String> assignedCohortIds, String cohortId, String queryStr,
            LocalDate targetDate, String statusFilter) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        List<String> targetCohortIds = (cohortId != null && !cohortId.isBlank())
                ? List.of(cohortId) : assignedCohortIds;
        if (targetCohortIds == null || targetCohortIds.isEmpty()) {
            return List.of();
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

        Set<String> studentIds = students.stream().map(User::getId).collect(Collectors.toSet());
        List<Attendance> existingAttendance = studentIds.isEmpty() ? List.of()
                : attendanceRepository.findByStudentIdIn(studentIds).stream()
                    .filter(record -> date.equals(record.getDate()))
                    .collect(Collectors.toList());
        Map<String, Attendance> attendanceByStudent = existingAttendance.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, record -> record, (first, ignored) -> first));

        List<ExcuseRequest> excuses = studentIds.isEmpty() ? List.of()
                : excuseRepository.findByStudentIdIn(studentIds).stream()
                    .filter(excuse -> excuse.getStatus() == ExcuseRequest.Status.ACCEPTED
                            || excuse.getStatus() == ExcuseRequest.Status.APPROVED)
                    .filter(excuse -> excuse.getStartDate() != null && !date.isBefore(excuse.getStartDate())
                            && !date.isAfter(excuse.getStartDate().plusDays(Math.max(1, excuse.getNumberOfDays()) - 1)))
                    .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, excuse -> excuse, (first, ignored) -> first));
        Map<String, Cohort> cohortsById = cohortRepository.findAllById(targetCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, cohort -> cohort, (first, ignored) -> first));

        List<AttendanceResponseDto.AttendanceRecord> records = students.stream().map(student -> {
            Cohort cohort = student.getCohortId() != null ? cohortsById.get(student.getCohortId()) : null;
            Attendance attendance = attendanceByStudent.get(student.getId());
            ExcuseRequest excuse = excuseByStudent.get(student.getId());
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            String status = weekend
                    ? (attendance != null && attendance.getStatus() != null
                            && attendance.getStatus() != Attendance.AttendanceStatus.ABSENT
                            ? attendance.getStatus().name() : "WEEKEND")
                    : (attendance != null
                            ? (attendance.getStatus() != null ? attendance.getStatus().name() : "ABSENT")
                            : (excuse != null ? "EXCUSED" : "ABSENT"));

            return new AttendanceResponseDto.AttendanceRecord(
                    attendance != null ? attendance.getId() : null,
                    student.getId(), student.getName(), student.getRegistrationNumber(),
                    student.getCohortId(), cohort != null ? cohort.getName() : student.getCohortId(),
                    date, attendance != null ? attendance.getMarkedAt() : null, status,
                    attendance != null && attendance.isManual(),
                    attendance != null ? attendance.getManualReason() : null, null);
        }).collect(Collectors.toList());

        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            String filter = statusFilter.trim();
            records = records.stream()
                    .filter(record -> record.getStatus() != null && record.getStatus().equalsIgnoreCase(filter))
                    .collect(Collectors.toList());
        }
        sortFacilitatorAttendanceRecords(records);
        return records;
    }

    public void sortFacilitatorAttendanceRecords(List<AttendanceResponseDto.AttendanceRecord> records) {
        records.sort((first, second) -> {
            Instant firstTime = first.getMarkedAt();
            Instant secondTime = second.getMarkedAt();
            if (firstTime != null && secondTime != null) {
                int comparison = firstTime.compareTo(secondTime);
                if (comparison != 0) return comparison;
            } else if (firstTime != null) {
                return -1;
            } else if (secondTime != null) {
                return 1;
            }
            String firstName = first.getStudentName() != null ? first.getStudentName() : "";
            String secondName = second.getStudentName() != null ? second.getStudentName() : "";
            return firstName.compareToIgnoreCase(secondName);
        });
    }

    public boolean isAttendedRecord(AttendanceResponseDto.AttendanceRecord record) {
        if (record == null || record.getStatus() == null) return false;
        String status = record.getStatus().toUpperCase();
        return "PRESENT".equals(status) || "LATE".equals(status) || "EARLY".equals(status)
                || (record.getMarkedAt() != null && !"ABSENT".equals(status) && !"EXCUSED".equals(status)
                && !"WEEKEND".equals(status) && !"HOLIDAY".equals(status));
    }
}
