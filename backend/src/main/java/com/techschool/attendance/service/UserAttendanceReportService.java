package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.ExcuseRequest;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.ExcuseRequestRepository;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAttendanceReportService {

    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final ExcuseRequestRepository excuseRepository;
    private final HolidayService holidayService;
    private final MongoTemplate mongoTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.attendance.timezone:Africa/Lagos}")
    private String timezone;

    public AnalyticsResponseDto.PageResponse<UserResponseDto.StudentAttendanceResponse> searchStudentsAdmin(
            String cohortId, String query,
            LocalDate startDate, LocalDate endDate,
            String statusStr,
            int page, int size,
            String sort, String order) {

        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);

        Criteria criteria = Criteria.where("role").is(User.Role.STUDENT);

        if (cohortId != null && !cohortId.isBlank()) {
            criteria.and("cohortId").is(cohortId.trim());
        }

        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim();
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(q), java.util.regex.Pattern.CASE_INSENSITIVE);
            criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("name").regex(regex),
                    Criteria.where("email").regex(regex),
                    Criteria.where("registrationNumber").regex(regex)
            ));
        }

        final LocalDate effStart = startDate;
        final LocalDate effEnd = endDate;
        final LocalDate targetDate = effStart != null ? effStart : LocalDate.now(ZoneId.of(timezone));

        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr.trim())) {
            String s = statusStr.trim().toUpperCase();
            Set<String> matchingIds = new HashSet<>();

            List<Attendance> dateAtt = attendanceRepository.findByDate(targetDate);
            Map<String, Attendance> attByStudent = dateAtt.stream()
                    .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

            List<ExcuseRequest> excuses = excuseRepository.findActiveExcusesOnOrBefore(targetDate).stream()
                    .filter(e -> e.getStartDate() != null && !targetDate.isBefore(e.getStartDate()) && !targetDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                    .collect(Collectors.toList());
            Set<String> excusedStudentIds = excuses.stream().map(ExcuseRequest::getStudentId).collect(Collectors.toSet());

            Query candidateQuery = Query.query(criteria);
            candidateQuery.fields().include("_id");
            List<User> candidates = mongoTemplate.find(candidateQuery, User.class);

            for (User u : candidates) {
                Attendance a = attByStudent.get(u.getId());
                boolean hasExcuse = excusedStudentIds.contains(u.getId()) || (a != null && a.getStatus() == Attendance.AttendanceStatus.EXCUSED);

                if ("PRESENT".equals(s) && a != null && (a.getStatus() == Attendance.AttendanceStatus.PRESENT || a.getStatus() == Attendance.AttendanceStatus.LATE)) {
                    matchingIds.add(u.getId());
                } else if ("EARLY".equals(s) && a != null && a.getStatus() == Attendance.AttendanceStatus.PRESENT) {
                    matchingIds.add(u.getId());
                } else if ("LATE".equals(s) && a != null && a.getStatus() == Attendance.AttendanceStatus.LATE) {
                    matchingIds.add(u.getId());
                } else if ("EXCUSED".equals(s) && hasExcuse) {
                    matchingIds.add(u.getId());
                } else if ("ABSENT".equals(s) && a == null && !hasExcuse) {
                    matchingIds.add(u.getId());
                }
            }
            criteria.and("_id").in(matchingIds);
        }

        long total = mongoTemplate.count(Query.query(criteria), User.class);

        boolean asc = !"desc".equalsIgnoreCase(order);
        boolean sortByRate = "rate".equalsIgnoreCase(sort) || "attendancerate".equalsIgnoreCase(sort) || "attendance".equalsIgnoreCase(sort);

        List<User> pageUsers;
        Map<String, UserResponseDto.StudentAttendanceResponse> responsesByStudent;

        if (sortByRate) {
            List<User> allFiltered = mongoTemplate.find(Query.query(criteria), User.class);
            Map<String, Cohort> cohortsById = loadCohortsById(allFiltered);
            responsesByStudent = loadStudentAttendanceResponses(allFiltered, effStart, effEnd, cohortsById);
            allFiltered.sort((u1, u2) -> {
                UserResponseDto.StudentAttendanceResponse r1 = responsesByStudent.get(u1.getId());
                UserResponseDto.StudentAttendanceResponse r2 = responsesByStudent.get(u2.getId());
                double rate1 = r1 != null ? r1.getAttendanceRate() : 0.0;
                double rate2 = r2 != null ? r2.getAttendanceRate() : 0.0;
                return asc ? Double.compare(rate1, rate2) : Double.compare(rate2, rate1);
            });
            int from = Math.min(safePage * safeSize, (int) total);
            int to = Math.min(from + safeSize, (int) total);
            pageUsers = allFiltered.subList(from, to);
        } else {
            String sortProp = "name";
            if ("email".equalsIgnoreCase(sort)) sortProp = "email";
            else if ("registrationnumber".equalsIgnoreCase(sort) || "registration".equalsIgnoreCase(sort)) sortProp = "registrationNumber";

            Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, sortProp));
            Query pageQuery = Query.query(criteria).with(pageable);

            pageUsers = mongoTemplate.find(pageQuery, User.class);
            Map<String, Cohort> cohortsById = loadCohortsById(pageUsers);
            responsesByStudent = loadStudentAttendanceResponses(pageUsers, effStart, effEnd, cohortsById);
        }

        List<UserResponseDto.StudentAttendanceResponse> content = pageUsers.stream()
                .map(u -> responsesByStudent.get(u.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new AnalyticsResponseDto.PageResponse<>(content, safePage, safeSize, (int) total,
                (int) Math.ceil((double) total / safeSize));
    }

    private Map<String, UserResponseDto.StudentAttendanceResponse> loadStudentAttendanceResponses(
            List<User> students, LocalDate startDate, LocalDate endDate, Map<String, Cohort> cohortsById) {
        Set<String> ids = students.stream().map(User::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();

        List<Attendance> all;
        if (startDate != null && endDate != null) {
            all = attendanceRepository.findByStudentIdInAndDateBetween(ids, startDate, endDate);
        } else {
            all = attendanceRepository.findByStudentIdIn(ids);
        }

        Map<String, List<Attendance>> byStudent = all.stream()
                .collect(Collectors.groupingBy(Attendance::getStudentId));

        List<ExcuseRequest> excuses = excuseRepository.findByStudentIdIn(ids).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .collect(Collectors.toList());
        Map<String, List<ExcuseRequest>> excusesByStudent = excuses.stream()
                .collect(Collectors.groupingBy(ExcuseRequest::getStudentId));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        Map<String, UserResponseDto.StudentAttendanceResponse> out = new HashMap<>();
        for (User u : students) {
            List<Attendance> att = byStudent.getOrDefault(u.getId(), List.of());
            List<ExcuseRequest> stExcuses = excusesByStudent.getOrDefault(u.getId(), List.of());

            LocalDate effStart = startDate;
            LocalDate effEnd = endDate;
            if (effStart == null || effEnd == null) {
                LocalDate creationDate = u.getCreatedAt() != null
                        ? ZonedDateTime.ofInstant(u.getCreatedAt(), ZoneId.of(timezone)).toLocalDate() : today;
                LocalDate earliestAtt = att.stream().map(Attendance::getDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(creationDate);
                effStart = creationDate.isBefore(earliestAtt) ? creationDate : earliestAtt;
                if (effStart.isAfter(today)) effStart = today;
                effEnd = today;
            }

            Set<LocalDate> holidays = holidayService.holidayDatesBetween(effStart, effEnd, u.getCohortId());
            Map<LocalDate, Attendance> attMap = att.stream()
                    .collect(Collectors.toMap(Attendance::getDate, Function.identity(), (a, b) -> a));

            int present = 0, late = 0, absent = 0, excused = 0, holiday = 0, totalDays = 0;
            for (LocalDate d = effStart; !d.isAfter(effEnd); d = d.plusDays(1)) {
                if (!holidayService.isSchoolDay(d, holidays)) continue;
                totalDays++;
                final LocalDate currDate = d;
                Attendance a = attMap.get(d);
                boolean isExcused = stExcuses.stream().anyMatch(e -> e.getStartDate() != null &&
                        !currDate.isBefore(e.getStartDate()) && !currDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)));

                if (a != null) {
                    if (a.getStatus() == Attendance.AttendanceStatus.PRESENT) present++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.LATE) late++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.EXCUSED) excused++;
                    else if (a.getStatus() == Attendance.AttendanceStatus.HOLIDAY) holiday++;
                    else absent++;
                } else if (isExcused) {
                    excused++;
                } else {
                    absent++;
                }
            }

            int attended = present + late;
            double rate = (totalDays - excused) > 0 ? (double) attended / (totalDays - excused) * 100.0
                    : (attended > 0 ? 100.0 : 0.0);
            String rating = rate >= 90 ? "EXCELLENT" : rate >= 75 ? "GOOD" : rate >= 50 ? "FAIR" : "POOR";

            LocalDate lastDate = att.stream()
                    .map(Attendance::getDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            Cohort c = u.getCohortId() != null ? cohortsById.get(u.getCohortId()) : null;
            String cohortName = c != null ? c.getName() : (u.getCohortId() != null ? u.getCohortId() : "—");

            UserResponseDto.StudentAttendanceResponse resp = new UserResponseDto.StudentAttendanceResponse(
                    u.getId(),
                    u.getName(),
                    u.getRegistrationNumber(),
                    u.getEmail(),
                    u.getCohortId(),
                    cohortName,
                    Math.round(rate * 10.0) / 10.0,
                    present,
                    absent,
                    excused,
                    late,
                    holiday,
                    totalDays,
                    rating,
                    lastDate,
                    u.isActive(),
                    u.getCreatedAt()
            );
            out.put(u.getId(), resp);
        }
        return out;
    }

    public ResponseEntity<byte[]> exportStudentsAdmin(String cohortId, String query,
                                                     LocalDate startDate, LocalDate endDate,
                                                     String status, String format, ExportService exportService) {
        AnalyticsResponseDto.PageResponse<UserResponseDto.StudentAttendanceResponse> page =
                searchStudentsAdmin(cohortId, query, startDate, endDate, status, 0, 10000, "name", "asc");

        List<List<Object>> table = new java.util.ArrayList<>();
        for (UserResponseDto.StudentAttendanceResponse r : page.getContent()) {
            table.add(List.of(
                    r.getName() != null ? r.getName() : "",
                    r.getRegistrationNumber() != null ? r.getRegistrationNumber() : "",
                    r.getEmail() != null ? r.getEmail() : "",
                    r.getCohortName() != null ? r.getCohortName() : "",
                    String.format("%.1f", r.getAttendanceRate()) + "%",
                    String.valueOf(r.getPresentDays()),
                    String.valueOf(r.getAbsentDays()),
                    String.valueOf(r.getExcusedDays()),
                    String.valueOf(r.getLateDays()),
                    String.valueOf(r.getHolidayCount()),
                    String.valueOf(r.getTotalAttendanceDays()),
                    r.getRating() != null ? r.getRating() : "",
                    r.getLastAttendanceDate() != null ? r.getLastAttendanceDate().toString() : "N/A"
            ));
        }

        List<String> headers = List.of(
                "Student Name", "Registration No", "Email", "Cohort",
                "Attendance %", "Present Days", "Absent Days", "Excused Days",
                "Late Days", "Holiday Count", "Total Days", "Rating", "Last Attendance Date"
        );

        String baseName = "students_attendance";
        if (cohortId != null && !cohortId.isBlank()) {
            baseName += "_cohort_" + cohortId;
        }

        return exportService.export(headers, table, format, baseName);
    }

    private Map<String, Cohort> loadCohortsById(List<User> users) {
        Set<String> ids = users.stream().map(User::getCohortId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return cohortRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));
    }
}