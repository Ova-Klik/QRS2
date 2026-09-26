package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.ExcuseRequest;
import com.techschool.attendance.data.model.QrSession;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.ExcuseRequestRepository;
import com.techschool.attendance.data.repository.QrSessionRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.dto.response.CohortResponseDto;
import com.techschool.attendance.dto.response.DashboardResponseDto;
import com.techschool.attendance.dto.response.QrResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohortFacilitatorDashboardStatsService {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final QrSessionRepository qrSessionRepository;
    private final ExcuseRequestRepository excuseRepository;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public DashboardResponseDto.FacilitatorStats buildFacilitatorStats(
            String targetCohortId, String queryStr, LocalDate targetDate, int page, int size,
            List<CohortResponseDto.CohortResponse> myCohorts) throws Exception {
        List<String> assignedCohortIds = myCohorts.stream()
                .map(CohortResponseDto.CohortResponse::getId).collect(Collectors.toList());
        List<String> activeCohortIds;
        if (targetCohortId != null && !targetCohortId.isBlank()) {
            if (!assignedCohortIds.contains(targetCohortId)) {
                throw AppException.forbidden("Access denied for cohort " + targetCohortId);
            }
            activeCohortIds = List.of(targetCohortId);
        } else {
            activeCohortIds = assignedCohortIds;
        }

        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        LocalDate date = targetDate != null ? targetDate : today;
        DayOfWeek dow = date.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        List<User> myStudents = activeCohortIds.isEmpty() ? List.of() : userRepository.findByCohortIdIn(activeCohortIds);
        String q = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!q.isEmpty()) {
            myStudents = myStudents.stream().filter(s ->
                    (s.getName() != null && s.getName().toLowerCase().contains(q))
                            || (s.getEmail() != null && s.getEmail().toLowerCase().contains(q))
                            || (s.getRegistrationNumber() != null && s.getRegistrationNumber().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        int totalStudents = myStudents.size();
        Set<String> studentIds = myStudents.stream().map(User::getId).collect(Collectors.toSet());
        List<Attendance> dateAtt = (studentIds.isEmpty() || isWeekend) ? List.of()
                : attendanceRepository.findByStudentIdIn(studentIds).stream()
                    .filter(a -> date.equals(a.getDate())).collect(Collectors.toList());
        Map<String, Attendance> attByStudent = dateAtt.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));
        List<ExcuseRequest> excuses = (studentIds.isEmpty() || isWeekend) ? List.of()
                : excuseRepository.findByStudentIdIn(studentIds).stream()
                    .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                    .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate())
                            && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                    .collect(Collectors.toList());
        Map<String, ExcuseRequest> excuseByStudent = excuses.stream()
                .collect(Collectors.toMap(ExcuseRequest::getStudentId, Function.identity(), (a, b) -> a));

        int present = 0, late = 0, excused = 0, absent = 0;
        if (!isWeekend) {
            for (User s : myStudents) {
                Attendance a = attByStudent.get(s.getId());
                ExcuseRequest exc = excuseByStudent.get(s.getId());
                if (a != null) {
                    if (a.getStatus() == Attendance.AttendanceStatus.PRESENT) present++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.LATE) late++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.EXCUSED) excused++;
                    else absent++;
                } else if (exc != null) excused++;
                else absent++;
            }
        }
        double rate = (totalStudents > 0 && !isWeekend) ? (double) (present + late) / totalStudents * 100.0 : 0.0;

        boolean hasActiveQr = false;
        QrResponseDto.QrResponse activeSession = null;
        List<QrSession> activeSessions = activeCohortIds.isEmpty() ? List.of()
                : qrSessionRepository.findActiveSessionsByCohortIds(activeCohortIds);
        for (QrSession s : activeSessions) {
            if (s.getExpiresAt() != null && s.getExpiresAt().isAfter(Instant.now())) {
                hasActiveQr = true;
                break;
            }
        }
        Map<String, Cohort> cohortsById = cohortRepository.findAllById(activeCohortIds).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));
        List<AttendanceResponseDto.AttendanceRecord> allRecords = myStudents.stream().map(s -> {
            Cohort c = s.getCohortId() != null ? cohortsById.get(s.getCohortId()) : null;
            Attendance a = attByStudent.get(s.getId());
            ExcuseRequest exc = excuseByStudent.get(s.getId());
            String status = isWeekend
                    ? ((a != null && a.getStatus() != null && a.getStatus() != Attendance.AttendanceStatus.ABSENT)
                        ? a.getStatus().name() : "WEEKEND")
                    : (a != null ? (a.getStatus() != null ? a.getStatus().name() : "ABSENT")
                        : (exc != null ? "EXCUSED" : "ABSENT"));
            return new AttendanceResponseDto.AttendanceRecord(
                    a != null ? a.getId() : null, s.getId(), s.getName(), s.getRegistrationNumber(),
                    s.getCohortId(), c != null ? c.getName() : s.getCohortId(), date,
                    a != null ? a.getMarkedAt() : null, status, a != null && a.isManual(),
                    a != null ? a.getManualReason() : null, null);
        }).collect(Collectors.toList());

        AttendanceService.sortFacilitatorAttendanceRecords(allRecords);
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, totalStudents);
        int to = Math.min(from + safeSize, totalStudents);
        List<AttendanceResponseDto.AttendanceRecord> pagedRecords = allRecords.subList(from, to);
        AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> pageResponse =
                new AnalyticsResponseDto.PageResponse<>(pagedRecords, safePage, safeSize, totalStudents,
                        (int) Math.ceil((double) totalStudents / safeSize));
        return new DashboardResponseDto.FacilitatorStats(totalStudents, present, late, absent, excused,
                Math.round(rate * 10.0) / 10.0, hasActiveQr, isWeekend, activeSession, pagedRecords, pageResponse);
    }
}