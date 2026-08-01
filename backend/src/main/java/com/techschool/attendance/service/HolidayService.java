package com.techschool.attendance.service;

import com.techschool.attendance.dto.HolidayDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.model.Cohort;
import com.techschool.attendance.model.Holiday;
import com.techschool.attendance.repository.CohortRepository;
import com.techschool.attendance.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final CohortRepository cohortRepository;
    private final AuditService auditService;

    public record HolidayInfo(String name, String reason, boolean custom) {}

    // ── Admin CRUD ─────────────────────────────────────────

    public List<HolidayDto.Response> getAll() {
        List<Holiday> holidays = holidayRepository.findAll();
        Set<String> cohortIds = holidays.stream()
                .filter(h -> !h.isAppliesToAll() && h.getCohortId() != null)
                .map(Holiday::getCohortId)
                .collect(Collectors.toSet());
        Map<String, String> cohortNames = cohortIds.isEmpty() ? Map.of()
                : cohortRepository.findAllById(cohortIds).stream()
                        .collect(Collectors.toMap(Cohort::getId, Cohort::getName));
        return holidays.stream()
                .sorted((a, b) -> {
                    int byDate = b.getStartDate().compareTo(a.getStartDate());
                    return byDate != 0 ? byDate : b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(h -> toResponse(h, cohortNames))
                .collect(Collectors.toList());
    }

    public HolidayDto.Response create(String actorId, String actorName, HolidayDto.CreateRequest request) {
        validate(request.getStartDate(), request.getEndDate(), request.getCohortId(), request.isAppliesToAll());
        Holiday h = new Holiday();
        h.setName(request.getName().trim());
        h.setStartDate(request.getStartDate());
        h.setEndDate(request.getEndDate());
        h.setReason(request.getReason());
        h.setAppliesToAll(request.isAppliesToAll());
        h.setCohortId(request.isAppliesToAll() ? null : request.getCohortId());
        h.setActive(true);
        h.setCreatedById(actorId);
        h.setCreatedByName(actorName);
        Holiday saved = holidayRepository.save(h);

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.USER_UPDATED, saved.getId(), saved.getName(),
                "Holiday created: " + saved.getName() + " (" + saved.getStartDate() + " – " + saved.getEndDate() + ")", null);
        return toResponse(saved);
    }

    public HolidayDto.Response update(String actorId, String actorName, String id, HolidayDto.UpdateRequest request) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Holiday not found"));
        if (request.getName() != null && !request.getName().isBlank()) h.setName(request.getName().trim());
        if (request.getStartDate() != null) h.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) h.setEndDate(request.getEndDate());
        if (request.getReason() != null) h.setReason(request.getReason());
        if (request.getAppliesToAll() != null) {
            h.setAppliesToAll(request.getAppliesToAll());
            h.setCohortId(Boolean.TRUE.equals(request.getAppliesToAll()) ? null : request.getCohortId());
        } else if (request.getCohortId() != null) {
            h.setCohortId(request.getCohortId());
        }
        validate(h.getStartDate(), h.getEndDate(), h.getCohortId(), h.isAppliesToAll());
        Holiday saved = holidayRepository.save(h);

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.USER_UPDATED, saved.getId(), saved.getName(),
                "Holiday updated: " + saved.getName(), null);
        return toResponse(saved);
    }

    public void delete(String actorId, String actorName, String id) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Holiday not found"));
        holidayRepository.delete(h);
        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.USER_UPDATED, id, h.getName(),
                "Holiday deleted: " + h.getName(), null);
    }

    public HolidayDto.Response toggle(String actorId, String actorName, String id) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Holiday not found"));
        h.setActive(!h.isActive());
        Holiday saved = holidayRepository.save(h);
        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.USER_UPDATED, saved.getId(), saved.getName(),
                "Holiday " + (saved.isActive() ? "activated" : "deactivated") + ": " + saved.getName(), null);
        return toResponse(saved);
    }

    private void validate(LocalDate start, LocalDate end, String cohortId, boolean appliesToAll) {
        if (start == null || end == null) throw AppException.badRequest("Start and end dates are required");
        if (end.isBefore(start)) throw AppException.badRequest("End date cannot be before start date");
        if (!appliesToAll && (cohortId == null || cohortId.isBlank())) {
            throw AppException.badRequest("Select a cohort when the holiday does not apply to all cohorts");
        }
        if (!appliesToAll && cohortId != null && !cohortRepository.existsById(cohortId)) {
            throw AppException.notFound("Cohort not found");
        }
    }

    // ── Holiday detection ──────────────────────────────────

    /** Returns the holiday covering {@code date} for the given cohort, if any. */
    public Optional<HolidayInfo> findHoliday(LocalDate date, String cohortId) {
        if (date == null) return Optional.empty();
        // Custom (admin-defined) holidays
        for (Holiday h : holidayRepository.findByActiveAndStartDateLessThanEqualOrderByStartDateDesc(true, date)) {
            if (h.isInRange(date) && (h.isAppliesToAll() || (h.getCohortId() != null && h.getCohortId().equals(cohortId)))) {
                return Optional.of(new HolidayInfo(h.getName(), h.getReason(), true));
            }
        }
        // Recognised public holidays
        String ph = publicHolidayName(date);
        if (ph != null) return Optional.of(new HolidayInfo(ph, "Public holiday", false));
        return Optional.empty();
    }

    public boolean isHoliday(LocalDate date, String cohortId) {
        return findHoliday(date, cohortId).isPresent();
    }

    /** A school day is a weekday that is not a holiday. */
    public boolean isSchoolDay(LocalDate date, String cohortId) {
        if (date == null) return false;
        if (date.getDayOfWeek().getValue() >= 6) return false; // Saturday/Sunday
        return !isHoliday(date, cohortId);
    }

    /**
     * Every holiday date (custom + public) in {@code [start, end]} for the cohort,
     * resolved with a single database query so day-by-day analytics don't hit the DB.
     */
    public Set<LocalDate> holidayDatesBetween(LocalDate start, LocalDate end, String cohortId) {
        return holidayNamesBetween(start, end, cohortId).keySet();
    }

    /**
     * Maps every holiday date in {@code [start, end]} for the cohort to its name,
     * resolved with a single database query.
     */
    public Map<LocalDate, String> holidayNamesBetween(LocalDate start, LocalDate end, String cohortId) {
        Map<LocalDate, String> map = new java.util.LinkedHashMap<>();
        if (start == null || end == null || end.isBefore(start)) return map;
        for (Holiday h : holidayRepository.findByActive(true)) {
            if (!h.isAppliesToAll() && (h.getCohortId() == null || !h.getCohortId().equals(cohortId))) continue;
            LocalDate d = h.getStartDate();
            while (!d.isAfter(h.getEndDate())) {
                if (!d.isBefore(start) && !d.isAfter(end)) map.putIfAbsent(d, h.getName());
                d = d.plusDays(1);
            }
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String ph = publicHolidayName(d);
            if (ph != null) map.putIfAbsent(d, ph);
        }
        return map;
    }

    /** A school day is a weekday that is not in the preloaded holiday set. */
    public boolean isSchoolDay(LocalDate date, Set<LocalDate> holidays) {
        if (date == null) return false;
        if (date.getDayOfWeek().getValue() >= 6) return false; // Saturday/Sunday
        return !holidays.contains(date);
    }

    /** All holidays overlapping the given range for the cohort (custom + public). */
    public List<HolidayInfo> holidaysInRange(LocalDate start, LocalDate end, String cohortId) {
        List<HolidayInfo> result = new ArrayList<>();
        for (Holiday h : holidayRepository.findByActive(true)) {
            if (h.isInRange(start) || h.isInRange(end) || (start.isBefore(h.getStartDate()) && end.isAfter(h.getEndDate()))) {
                if (h.isAppliesToAll() || (h.getCohortId() != null && h.getCohortId().equals(cohortId))) {
                    result.add(new HolidayInfo(h.getName(), h.getReason(), true));
                }
            }
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String ph = publicHolidayName(d);
            if (ph != null) result.add(new HolidayInfo(ph, "Public holiday", false));
        }
        return result;
    }

    // ── Public holiday calendar (Nigeria) ──────────────────

    private String publicHolidayName(LocalDate d) {
        if (d.getMonthValue() == 1 && d.getDayOfMonth() == 1) return "New Year's Day";
        if (d.getMonthValue() == 5 && d.getDayOfMonth() == 1) return "Workers' Day";
        if (d.getMonthValue() == 6 && d.getDayOfMonth() == 12) return "Democracy Day";
        if (d.getMonthValue() == 10 && d.getDayOfMonth() == 1) return "Independence Day";
        if (d.getMonthValue() == 12 && d.getDayOfMonth() == 25) return "Christmas Day";
        if (d.getMonthValue() == 12 && d.getDayOfMonth() == 26) return "Boxing Day";

        LocalDate easter = easterSunday(d.getYear());
        if (d.equals(easter.minusDays(2))) return "Good Friday";
        if (d.equals(easter.plusDays(1))) return "Easter Monday";
        return null;
    }

    /** Anonymous Gregorian algorithm — computes Easter Sunday for a given year. */
    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private HolidayDto.Response toResponse(Holiday h) {
        return toResponse(h, Map.of());
    }

    private HolidayDto.Response toResponse(Holiday h, Map<String, String> cohortNames) {
        String cohortName = null;
        if (!h.isAppliesToAll() && h.getCohortId() != null) {
            cohortName = cohortNames.get(h.getCohortId());
        }
        return new HolidayDto.Response(
                h.getId(), h.getName(), h.getStartDate(), h.getEndDate(), h.getReason(),
                h.isAppliesToAll(), h.getCohortId(), cohortName, h.isActive(), h.getCreatedAt());
    }
}
