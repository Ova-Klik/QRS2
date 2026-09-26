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
import com.techschool.attendance.dto.response.CohortResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohortReadService {

    private final CohortRepository cohortRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final ExcuseRequestRepository excuseRepository;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public List<CohortResponseDto.CohortResponse> getAllCohorts() {
        return toResponses(cohortRepository.findAll());
    }

    public List<CohortResponseDto.CohortResponse> getActiveCohorts() {
        return toResponses(cohortRepository.findByActive(true));
    }

    public List<CohortResponseDto.CohortResponse> getCohortsByFacilitator(String facilitatorId) {
        User facilitator = userRepository.findById(facilitatorId).orElse(null);
        Set<String> cohortIds = cohortRepository.findByFacilitatorId(facilitatorId).stream()
                .map(Cohort::getId).collect(Collectors.toSet());
        if (facilitator != null && facilitator.getAssignedCohortIds() != null) {
            cohortIds.addAll(facilitator.getAssignedCohortIds());
        }
        return cohortIds.isEmpty() ? List.of() : toResponses(cohortRepository.findAllById(cohortIds));
    }

    public AnalyticsResponseDto.PageResponse<CohortResponseDto.CohortResponse> searchCohorts(
            String query, String status, int page, int size, String sort, String order) {
        return searchCohorts(query, status, null, null, page, size, sort, order);
    }

    public AnalyticsResponseDto.PageResponse<CohortResponseDto.CohortResponse> searchCohorts(
            String query, String status, LocalDate targetDate, String cohortIdFilter,
            int page, int size, String sort, String order) {
        List<Cohort> filtered = cohortRepository.findAll().stream().filter(cohort -> {
            if (cohortIdFilter != null && !cohortIdFilter.isBlank()
                    && !cohort.getId().equals(cohortIdFilter.trim())) return false;
            if ("ACTIVE".equalsIgnoreCase(status)) return cohort.isActive();
            if ("ARCHIVED".equalsIgnoreCase(status)) return !cohort.isActive();
            return true;
        }).collect(Collectors.toList());

        Set<String> facilitatorIds = filtered.stream().map(Cohort::getFacilitatorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, User> facilitatorsById = facilitatorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(facilitatorIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first));

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        if (!normalizedQuery.isEmpty()) {
            filtered = filtered.stream().filter(cohort -> {
                boolean nameMatches = cohort.getName() != null
                        && cohort.getName().toLowerCase().contains(normalizedQuery);
                User facilitator = cohort.getFacilitatorId() != null
                        ? facilitatorsById.get(cohort.getFacilitatorId()) : null;
                boolean facilitatorMatches = facilitator != null && facilitator.getName() != null
                        && facilitator.getName().toLowerCase().contains(normalizedQuery);
                return nameMatches || facilitatorMatches;
            }).collect(Collectors.toList());
        }

        List<CohortResponseDto.CohortResponse> responses = toResponses(filtered, targetDate);
        boolean ascending = !"desc".equalsIgnoreCase(order);
        String sortKey = sort == null ? "name" : sort.toLowerCase().trim();
        Comparator<CohortResponseDto.CohortResponse> comparator;
        switch (sortKey) {
            case "students":
            case "studentcount":
                comparator = Comparator.comparingInt(CohortResponseDto.CohortResponse::getStudentCount);
                break;
            case "rate":
            case "attendancerate":
                comparator = Comparator.comparingDouble(CohortResponseDto.CohortResponse::getAttendanceRate);
                break;
            case "createdat":
            case "date":
                comparator = Comparator.comparing(CohortResponseDto.CohortResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            default:
                comparator = Comparator.comparing(CohortResponseDto.CohortResponse::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        responses.sort(ascending ? comparator : comparator.reversed());

        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = (int) Math.min((long) safePage * safeSize, responses.size());
        int to = Math.min(from + safeSize, responses.size());
        List<CohortResponseDto.CohortResponse> content = responses.subList(from, to);
        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, responses.size(),
                (int) Math.ceil((double) responses.size() / safeSize));
    }

    public CohortResponseDto.CohortResponse getCohortById(String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        return toResponses(List.of(cohort)).get(0);
    }

    public CohortResponseDto.CohortResponse toResponse(Cohort cohort) {
        return toResponses(List.of(cohort)).get(0);
    }

    public List<CohortResponseDto.CohortResponse> toResponses(List<Cohort> cohorts) {
        return toResponses(cohorts, null);
    }

    public List<CohortResponseDto.CohortResponse> toResponses(List<Cohort> cohorts, LocalDate targetDate) {
        if (cohorts.isEmpty()) return List.of();
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));

        Set<String> facilitatorIds = cohorts.stream().map(Cohort::getFacilitatorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, User> facilitatorsById = facilitatorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(facilitatorIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first));
        Map<String, List<User>> studentsByCohort = userRepository.findByRole(User.Role.STUDENT).stream()
                .filter(student -> student.getCohortId() != null)
                .collect(Collectors.groupingBy(User::getCohortId));

        List<String> cohortIds = cohorts.stream().map(Cohort::getId).collect(Collectors.toList());
        Map<String, List<Attendance>> attendanceByCohort = attendanceRepository.findByCohortIdIn(cohortIds).stream()
                .filter(record -> date.equals(record.getDate()))
                .collect(Collectors.groupingBy(Attendance::getCohortId));
        Map<String, List<ExcuseRequest>> excusesByStudent = excuseRepository.findAll().stream()
                .filter(excuse -> excuse.getStatus() == ExcuseRequest.Status.ACCEPTED
                        || excuse.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(excuse -> excuse.getStartDate() != null && !date.isBefore(excuse.getStartDate())
                        && !date.isAfter(excuse.getStartDate().plusDays(Math.max(1, excuse.getNumberOfDays()) - 1)))
                .collect(Collectors.groupingBy(ExcuseRequest::getStudentId));

        return cohorts.stream().map(cohort -> {
            User facilitator = cohort.getFacilitatorId() != null
                    ? facilitatorsById.get(cohort.getFacilitatorId()) : null;
            List<User> students = studentsByCohort.getOrDefault(cohort.getId(), List.of());
            List<Attendance> attendance = attendanceByCohort.getOrDefault(cohort.getId(), List.of());
            int early = (int) attendance.stream()
                    .filter(record -> record.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) attendance.stream()
                    .filter(record -> record.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int present = early + late;
            int excused = 0;
            for (User student : students) {
                if (excusesByStudent.containsKey(student.getId()) || attendance.stream().anyMatch(record ->
                        record.getStudentId().equals(student.getId())
                                && record.getStatus() == Attendance.AttendanceStatus.EXCUSED)) {
                    excused++;
                }
            }
            int absent = Math.max(0, students.size() - (present + excused));
            double rate = students.size() - excused > 0
                    ? (double) present / (students.size() - excused) * 100.0 : 0.0;

            CohortResponseDto.CohortResponse response = new CohortResponseDto.CohortResponse();
            response.setId(cohort.getId());
            response.setName(cohort.getName());
            response.setFacilitatorId(cohort.getFacilitatorId());
            response.setFacilitatorName(facilitator != null ? facilitator.getName() : null);
            response.setFacilitatorEmail(facilitator != null ? facilitator.getEmail() : null);
            response.setFacilitatorPhone(facilitator != null ? facilitator.getPhone() : null);
            response.setSchedule(cohort.getSchedule());
            response.setActive(cohort.isActive());
            response.setStudentCount(students.size());
            response.setAttendanceRate(Math.round(rate * 10.0) / 10.0);
            response.setPresentCount(present);
            response.setEarlyCount(early);
            response.setLateCount(late);
            response.setAbsentCount(absent);
            response.setExcusedCount(excused);
            response.setTotalRecords(attendance.size());
            response.setCreatedAt(cohort.getCreatedAt());
            return response;
        }).collect(Collectors.toList());
    }
}
