package com.techschool.attendance.service;

import com.techschool.attendance.data.model.*;
import com.techschool.attendance.data.repository.*;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final HolidayService holidayService;
    private final ExcuseRequestRepository excuseRepository;
    private final DeviceRepository deviceRepository;

    @org.springframework.beans.factory.annotation.Value("${app.attendance.timezone}")
    private String timezone;

    public List<AttendanceResponseDto.AttendanceRecord> getStudentHistory(String studentId) {
        return buildRecords(attendanceRepository.findByStudentId(studentId));
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> getStudentHistoryPage(String studentId, int page, int size) {
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));
        Page<Attendance> result = attendanceRepository.findByStudentId(studentId, pageable);
        return new AnalyticsResponseDto.PageResponse<>(buildRecords(result.getContent()), safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    public AnalyticsResponseDto.DailySummary buildDailySummary(String cohortId, LocalDate date) {
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        List<Attendance> records = attendanceRepository.findByCohortIdAndDate(cohortId, date);
        Cohort cohort = cohortRepository.findById(cohortId).orElse(null);
        String cohortName = cohort != null ? cohort.getName() : cohortId;

        int present = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int holidayMarked = (int) records.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
        int manual = (int) records.stream().filter(Attendance::isManual).count();
        int total = students.size();

        boolean isHoliday = holidayService.isHoliday(date, cohortId);
        int holiday = isHoliday ? holidayMarked + Math.max(0, total - records.size()) : holidayMarked;
        int absent = isHoliday ? 0 : Math.max(0, total - records.size());
        double rate = total > 0 ? (double) (present + late) / total * 100 : 0;

        return new AttendanceResponseDto.DailySummary(
                date, cohortId, cohortName,
                total, present, late, absent, excused, holiday, manual, rate,
                buildRecords(records)
        );
    }

    public AnalyticsResponseDto.CalendarMonth buildCalendarMonth(String cohortId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        String cohortName = "All Cohorts";
        List<User> students;
        if (cohortId != null && !cohortId.isBlank()) {
            Cohort cohort = cohortRepository.findById(cohortId).orElse(null);
            cohortName = cohort != null ? cohort.getName() : cohortId;
            students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        } else {
            students = userRepository.findByRole(User.Role.STUDENT);
        }

        List<Attendance> records = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, first, last)
                : attendanceRepository.findByDateBetween(first, last);

        Map<LocalDate, List<Attendance>> byDay = records.stream().collect(Collectors.groupingBy(Attendance::getDate));
        int totalStudents = students.size();
        Map<LocalDate, String> holidays = holidayService.holidayNamesBetween(first, last,
                cohortId != null && !cohortId.isBlank() ? cohortId : null);

        List<AnalyticsResponseDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            boolean isHoliday = holidays.containsKey(d);
            String holidayName = holidays.get(d);

            List<Attendance> dayRecs = byDay.getOrDefault(d, List.of());
            int present = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int late = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int excused = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            int holidayCount = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
            int absentMarked = (int) dayRecs.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
            int absent = (weekend || isHoliday)
                    ? 0
                    : Math.max(0, totalStudents - dayRecs.size()) + absentMarked;

            days.add(new AnalyticsResponseDto.CalendarDay(
                    d, weekend, isHoliday, holidayName,
                    present, late, absent, excused, holidayCount, totalStudents));
        }

        return new AnalyticsResponseDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    public AnalyticsResponseDto.CalendarMonth buildStudentCalendarMonth(String studentId, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        User student = userRepository.findById(studentId).orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";

        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, first, last);
        Map<LocalDate, Attendance> byDay = records.stream().collect(Collectors.toMap(Attendance::getDate, Function.identity(), (a, b) -> a));
        Map<LocalDate, String> holidays = holidayService.holidayNamesBetween(first, last, cohortId);

        List<AnalyticsResponseDto.CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            boolean isHoliday = holidays.containsKey(d);
            Attendance rec = byDay.get(d);
            Attendance.AttendanceStatus st = rec != null ? rec.getStatus() : null;

            int present = st == Attendance.AttendanceStatus.PRESENT ? 1 : 0;
            int late = st == Attendance.AttendanceStatus.LATE ? 1 : 0;
            int excused = st == Attendance.AttendanceStatus.EXCUSED ? 1 : 0;
            int holidayCount = st == Attendance.AttendanceStatus.HOLIDAY ? 1 : 0;
            int absent = (!weekend && !isHoliday && (st == null || st == Attendance.AttendanceStatus.ABSENT)) ? 1 : 0;

            days.add(new AnalyticsResponseDto.CalendarDay(
                    d, weekend, isHoliday, holidays.get(d),
                    present, late, absent, excused, holidayCount, 1));
        }

        return new AnalyticsResponseDto.CalendarMonth(year, month, cohortId, cohortName, days);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(String cohortId, LocalDate start, LocalDate end, int page, int size) {
        return searchByDate(cohortId, start, end, null, page, size);
    }

    public AnalyticsResponseDto.PageResponse<AttendanceResponseDto.AttendanceRecord> searchByDate(String cohortId, LocalDate start, LocalDate end, Integer lastNDays, int page, int size) {
        LocalDate[] range = resolveDateRange(start, end, lastNDays);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date"));

        Page<Attendance> result = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1], pageable)
                : attendanceRepository.findByDateBetween(range[0], range[1], pageable);

        return new AnalyticsResponseDto.PageResponse<>(buildRecords(result.getContent()), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public LocalDate[] resolveDateRange(LocalDate start, LocalDate end, Integer lastNDays) {
        if (lastNDays != null && lastNDays > 0) {
            int n = Math.min(730, lastNDays);
            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            return new LocalDate[]{today.minusDays(n - 1), today};
        }
        if (start == null || end == null) throw AppException.badRequest("Start and end dates are required");
        if (end.isBefore(start)) throw AppException.badRequest("End date cannot be before start date");
        return new LocalDate[]{start, end};
    }

    public List<AttendanceResponseDto.AttendanceRecord> findRecordsInRange(String cohortId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = (cohortId != null && !cohortId.isBlank())
                ? attendanceRepository.findByCohortIdAndDateBetween(cohortId, range[0], range[1])
                : attendanceRepository.findByDateBetween(range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    public List<AttendanceResponseDto.AttendanceRecord> findStudentRecordsInRange(String studentId, LocalDate start, LocalDate end) {
        LocalDate[] range = resolveDateRange(start, end, null);
        List<Attendance> records = attendanceRepository.findByStudentIdAndDateBetween(studentId, range[0], range[1]);
        records.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return buildRecords(records);
    }

    public AnalyticsResponseDto.StudentAnalytics buildStudentSummaryExport(String studentId) {
        return buildStudentAnalytics(studentId);
    }

    public AnalyticsResponseDto.StudentAnalytics buildStudentAnalytics(String studentId) {
        User student = userRepository.findById(studentId).orElseThrow(() -> AppException.notFound("Student not found"));
        String cohortId = student.getCohortId();
        String cohortName = cohortId != null ? cohortRepository.findById(cohortId).map(Cohort::getName).orElse(cohortId) : "Unassigned";
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByStudentIdOrderByDateAsc(studentId);
        Map<LocalDate, Attendance.AttendanceStatus> statusByDate = all.stream().collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a, LinkedHashMap::new));

        List<ExcuseRequest> excuses = excuseRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .collect(Collectors.toList());

        LocalDate creationDate = student.getCreatedAt() != null ? java.time.ZonedDateTime.ofInstant(student.getCreatedAt(), ZoneId.of(timezone)).toLocalDate() : today;
        LocalDate earliestAttDate = all.isEmpty() ? creationDate : all.get(0).getDate();
        LocalDate startDate = creationDate.isBefore(earliestAttDate) ? creationDate : earliestAttDate;
        if (startDate.isAfter(today)) startDate = today;

        Set<LocalDate> holidays = holidayService.holidayDatesBetween(startDate, today, cohortId);

        int schoolDays = 0, present = 0, late = 0, excused = 0, holiday = 0, absent = 0;
        int curAtt = 0, maxAtt = 0, curAbs = 0, maxAbs = 0;

        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            if (!holidayService.isSchoolDay(d, holidays)) continue;
            schoolDays++;
            final LocalDate currDate = d;
            Attendance.AttendanceStatus st = statusByDate.get(d);
            boolean isExcused = excuses.stream().anyMatch(e -> e.getStartDate() != null && !currDate.isBefore(e.getStartDate()) && !currDate.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)));

            if (st == Attendance.AttendanceStatus.PRESENT) {
                present++; curAtt++; curAbs = 0;
                if (curAtt > maxAtt) maxAtt = curAtt;
            } else if (st == Attendance.AttendanceStatus.LATE) {
                late++; curAtt++; curAbs = 0;
                if (curAtt > maxAtt) maxAtt = curAtt;
            } else if (st == Attendance.AttendanceStatus.EXCUSED || isExcused) {
                excused++;
            } else if (st == Attendance.AttendanceStatus.HOLIDAY) {
                schoolDays--;
                holiday++;
            } else {
                absent++; curAbs++; curAtt = 0;
                if (curAbs > maxAbs) maxAbs = curAbs;
            }
        }

        int attended = present + late;
        double rate = (schoolDays - excused) > 0 ? (double) attended / (schoolDays - excused) * 100.0 : (attended > 0 ? 100.0 : 0.0);
        String rating = rate >= 90 ? "EXCELLENT" : rate >= 75 ? "GOOD" : rate >= 50 ? "FAIR" : "POOR";

        List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> trend = buildMonthlyTrend(today, statusByDate, holidays);

        return new AnalyticsResponseDto.StudentAnalytics(
                studentId, student.getName(), cohortId, cohortName,
                Math.round(rate * 10.0) / 10.0, schoolDays, present, late, absent, excused, holiday, late,
                maxAtt, maxAbs, rating, trend);
    }

    private List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> buildMonthlyTrend(LocalDate today, Map<LocalDate, Attendance.AttendanceStatus> statusByDate, Set<LocalDate> holidays) {
        List<AnalyticsResponseDto.StudentAnalytics.MonthlyTrend> trend = new ArrayList<>();
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            LocalDate ms = monthStart.plusMonths(i);
            LocalDate me = ms.withDayOfMonth(ms.lengthOfMonth());
            int schoolDays = 0, attended = 0;
            for (LocalDate d = ms; !d.isAfter(me); d = d.plusDays(1)) {
                if (!holidayService.isSchoolDay(d, holidays)) continue;
                schoolDays++;
                Attendance.AttendanceStatus st = statusByDate.get(d);
                if (st == Attendance.AttendanceStatus.PRESENT || st == Attendance.AttendanceStatus.LATE) attended++;
            }
            double mRate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
            trend.add(new AnalyticsResponseDto.StudentAnalytics.MonthlyTrend(ms.getMonth().toString().substring(0, 3), ms.getYear(), mRate));
        }
        return trend;
    }

    public List<AnalyticsResponseDto.CohortExportRow> buildCohortExportRows(String cohortId) {
        if (!cohortRepository.existsById(cohortId)) {
            throw AppException.notFound("Cohort not found");
        }
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> all = attendanceRepository.findByCohortId(cohortId);
        Map<String, List<Attendance>> byStudent = all.stream().collect(Collectors.groupingBy(Attendance::getStudentId));

        LocalDate globalStart = today;
        for (List<Attendance> recs : byStudent.values()) {
            for (Attendance a : recs) {
                if (a.getDate().isBefore(globalStart)) globalStart = a.getDate();
            }
        }
        Set<LocalDate> holidays = holidayService.holidayDatesBetween(globalStart, today, cohortId);

        List<AnalyticsResponseDto.CohortExportRow> rows = new ArrayList<>();
        for (User s : students) {
            List<Attendance> recs = byStudent.getOrDefault(s.getId(), List.of());
            Map<LocalDate, Attendance.AttendanceStatus> statusByDate = recs.stream().collect(Collectors.toMap(Attendance::getDate, Attendance::getStatus, (a, b) -> a));
            LocalDate startDate = recs.isEmpty() ? today : recs.stream().map(Attendance::getDate).min(LocalDate::compareTo).orElse(today);

            int schoolDays = 0, attended = 0, present = 0, late = 0, excused = 0, holidayDays = 0;
            for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
                if (holidays.contains(d)) { holidayDays++; continue; }
                if (d.getDayOfWeek().getValue() >= 6) continue;
                schoolDays++;
                Attendance.AttendanceStatus st = statusByDate.get(d);
                if (st == null) continue;
                switch (st) {
                    case PRESENT -> { present++; attended++; }
                    case LATE -> { late++; attended++; }
                    case EXCUSED -> excused++;
                    default -> {}
                }
            }
            double rate = schoolDays > 0 ? (double) attended / schoolDays * 100 : 0;
            rows.add(new AnalyticsResponseDto.CohortExportRow(
                    s.getName(), s.getRegistrationNumber(), rate, present, late,
                    excused, holidayDays, attended,
                    Math.max(0, schoolDays - attended - excused), schoolDays));
        }
        rows.sort((a, b) -> a.getStudentName().compareToIgnoreCase(b.getStudentName()));
        return rows;
    }

    public AttendanceResponseDto.AttendanceRecord toRecord(Attendance a) {
        User student = userRepository.findById(a.getStudentId()).orElse(null);
        Cohort cohort = a.getCohortId() != null ? cohortRepository.findById(a.getCohortId()).orElse(null) : null;
        return new AttendanceResponseDto.AttendanceRecord(
                a.getId(), a.getStudentId(),
                student != null ? student.getName() : a.getStudentId(),
                student != null ? student.getRegistrationNumber() : null,
                a.getCohortId(), cohort != null ? cohort.getName() : a.getCohortId(),
                a.getDate(), a.getMarkedAt(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.isManual(), a.getManualReason(), null
        );
    }

    public List<AttendanceResponseDto.AttendanceRecord> buildRecords(List<Attendance> records) {
        if (records.isEmpty()) return List.of();

        Set<String> studentIds = records.stream().map(Attendance::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> cohortIds = records.stream().map(Attendance::getCohortId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> deviceIds = records.stream().map(Attendance::getDeviceId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, User> students = userRepository.findAllById(studentIds).stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<String, Cohort> cohorts = cohortRepository.findAllById(cohortIds).stream().collect(Collectors.toMap(Cohort::getId, Function.identity(), (a, b) -> a));
        Map<String, Device> devices = deviceRepository.findAllById(deviceIds).stream().collect(Collectors.toMap(Device::getId, Function.identity(), (a, b) -> a));

        return records.stream().map(a -> {
            User s = students.get(a.getStudentId());
            Cohort c = a.getCohortId() != null ? cohorts.get(a.getCohortId()) : null;
            Device d = a.getDeviceId() != null ? devices.get(a.getDeviceId()) : null;
            String deviceUsed = d != null ? (d.getFingerprint() != null ? d.getFingerprint() : d.getImei()) : null;
            boolean isWeekend = a.getDate() != null && a.getDate().getDayOfWeek().getValue() >= 6;
            String status = isWeekend && a.getStatus() == Attendance.AttendanceStatus.ABSENT ? "WEEKEND" : (a.getStatus() != null ? a.getStatus().name() : null);
            return new AttendanceResponseDto.AttendanceRecord(
                    a.getId(), a.getStudentId(),
                    s != null ? s.getName() : a.getStudentId(),
                    s != null ? s.getRegistrationNumber() : null,
                    a.getCohortId(), c != null ? c.getName() : a.getCohortId(),
                    a.getDate(), a.getMarkedAt(),
                    status,
                    a.isManual(), a.getManualReason(), deviceUsed);
        }).collect(Collectors.toList());
    }
}
