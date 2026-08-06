package com.techschool.attendance.service;

import com.techschool.attendance.dto.*;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CohortService {

    private final CohortRepository cohortRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final DeviceRepository deviceRepository;
    private final QrSessionRepository qrSessionRepository;
    private final AuditService auditService;
    private final MongoTemplate mongoTemplate;
    private final ExcuseRequestRepository excuseRepository;

    @org.springframework.beans.factory.annotation.Value("${app.attendance.timezone}")
    private String timezone;

    public CohortDto.CohortResponse createCohort(String actorId, String actorName,
                                                   CohortDto.CreateCohortRequest request) {
        Cohort cohort = new Cohort();
        cohort.setName(request.getName());
        cohort.setFacilitatorId(request.getFacilitatorId());
        cohort.setSchedule(request.getSchedule() != null ? request.getSchedule() : "7:00 AM – 8:30 AM");
        cohort.setDescription(request.getDescription());
        Cohort saved = cohortRepository.save(cohort);

        // Add cohort to facilitator's list
        userRepository.findById(request.getFacilitatorId()).ifPresent(fac -> {
            if (fac.getAssignedCohortIds() == null) fac.setAssignedCohortIds(new java.util.ArrayList<>());
            fac.getAssignedCohortIds().add(saved.getId());
            userRepository.save(fac);
        });

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.COHORT_CREATED, saved.getId(), saved.getName(),
                "Cohort created: " + saved.getName(), null);
        return toResponse(saved);
    }

    public List<CohortDto.CohortResponse> getAllCohorts() {
        return toResponses(cohortRepository.findAll());
    }

    public List<CohortDto.CohortResponse> getActiveCohorts() {
        return toResponses(cohortRepository.findByActive(true));
    }

    public List<CohortDto.CohortResponse> getCohortsByFacilitator(String facId) {
        User fac = userRepository.findById(facId).orElse(null);
        Set<String> cohortIds = new java.util.HashSet<>();
        cohortRepository.findByFacilitatorId(facId).forEach(c -> cohortIds.add(c.getId()));
        if (fac != null && fac.getAssignedCohortIds() != null) {
            cohortIds.addAll(fac.getAssignedCohortIds());
        }
        if (cohortIds.isEmpty()) return List.of();
        return toResponses(cohortRepository.findAllById(cohortIds));
    }

    /** Batched cohort mapping — zero per-cohort queries. */
    private List<CohortDto.CohortResponse> toResponses(List<Cohort> cohorts) {
        return toResponses(cohorts, null);
    }

    private List<CohortDto.CohortResponse> toResponses(List<Cohort> cohorts, LocalDate targetDate) {
        if (cohorts.isEmpty()) return List.of();

        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));

        Set<String> facilitatorIds = cohorts.stream()
                .map(Cohort::getFacilitatorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> facById = facilitatorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(facilitatorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Map<String, List<User>> studentsByCohort = userRepository.findByRole(User.Role.STUDENT).stream()
                .filter(u -> u.getCohortId() != null)
                .collect(Collectors.groupingBy(User::getCohortId));

        List<String> cohortIds = cohorts.stream().map(Cohort::getId).collect(Collectors.toList());

        List<Attendance> dateAtt = attendanceRepository.findByCohortIdIn(cohortIds).stream()
                .filter(a -> date.equals(a.getDate()))
                .collect(Collectors.toList());
        Map<String, List<Attendance>> attByCohort = dateAtt.stream()
                .collect(Collectors.groupingBy(Attendance::getCohortId));

        List<ExcuseRequest> excuses = excuseRepository.findAll().stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate()) && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
                .collect(Collectors.toList());
        Map<String, List<ExcuseRequest>> excuseByStudent = excuses.stream()
                .collect(Collectors.groupingBy(ExcuseRequest::getStudentId));

        return cohorts.stream().map(c -> {
            User fac = c.getFacilitatorId() != null ? facById.get(c.getFacilitatorId()) : null;
            List<User> cohortStudents = studentsByCohort.getOrDefault(c.getId(), List.of());
            int studentCount = cohortStudents.size();
            List<Attendance> att = attByCohort.getOrDefault(c.getId(), List.of());

            int earlyCount = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int lateCount = (int) att.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int presentCount = earlyCount + lateCount;

            int excusedCount = 0;
            for (User s : cohortStudents) {
                if (excuseByStudent.containsKey(s.getId())) {
                    excusedCount++;
                } else {
                    boolean isExcusedAtt = att.stream().anyMatch(a -> a.getStudentId().equals(s.getId()) && a.getStatus() == Attendance.AttendanceStatus.EXCUSED);
                    if (isExcusedAtt) excusedCount++;
                }
            }

            int absentCount = Math.max(0, studentCount - (presentCount + excusedCount));
            double attendanceRate = (studentCount - excusedCount) > 0
                    ? (double) presentCount / (studentCount - excusedCount) * 100.0
                    : 0.0;

            CohortDto.CohortResponse resp = new CohortDto.CohortResponse();
            resp.setId(c.getId());
            resp.setName(c.getName());
            resp.setFacilitatorId(c.getFacilitatorId());
            resp.setFacilitatorName(fac != null ? fac.getName() : null);
            resp.setFacilitatorEmail(fac != null ? fac.getEmail() : null);
            resp.setFacilitatorPhone(fac != null ? fac.getPhone() : null);
            resp.setSchedule(c.getSchedule());
            resp.setActive(c.isActive());
            resp.setStudentCount(studentCount);
            resp.setAttendanceRate(Math.round(attendanceRate * 10.0) / 10.0);
            resp.setPresentCount(presentCount);
            resp.setEarlyCount(earlyCount);
            resp.setLateCount(lateCount);
            resp.setAbsentCount(absentCount);
            resp.setExcusedCount(excusedCount);
            resp.setTotalRecords(att.size());
            resp.setCreatedAt(c.getCreatedAt());
            return resp;
        }).collect(Collectors.toList());
    }

    public AnalyticsDto.PageResponse<CohortDto.CohortResponse> searchCohorts(
            String query, String statusStr, int page, int size, String sort, String order) {
        return searchCohorts(query, statusStr, null, null, page, size, sort, order);
    }

    public AnalyticsDto.PageResponse<CohortDto.CohortResponse> searchCohorts(
            String query, String statusStr, LocalDate targetDate, String cohortIdFilter,
            int page, int size, String sort, String order) {

        List<Cohort> allCohorts = cohortRepository.findAll();

        List<Cohort> filtered = allCohorts.stream().filter(c -> {
            if (cohortIdFilter != null && !cohortIdFilter.isBlank()) {
                if (!c.getId().equals(cohortIdFilter.trim())) return false;
            }
            if ("ACTIVE".equalsIgnoreCase(statusStr)) return c.isActive();
            if ("ARCHIVED".equalsIgnoreCase(statusStr)) return !c.isActive();
            return true;
        }).collect(Collectors.toList());

        Set<String> facilitatorIds = filtered.stream()
                .map(Cohort::getFacilitatorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> facById = facilitatorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(facilitatorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        String q = query == null ? "" : query.trim().toLowerCase();
        if (!q.isEmpty()) {
            filtered = filtered.stream().filter(c -> {
                boolean nameMatch = c.getName() != null && c.getName().toLowerCase().contains(q);
                User fac = c.getFacilitatorId() != null ? facById.get(c.getFacilitatorId()) : null;
                boolean facMatch = fac != null && fac.getName() != null && fac.getName().toLowerCase().contains(q);
                return nameMatch || facMatch;
            }).collect(Collectors.toList());
        }

        List<CohortDto.CohortResponse> responses = toResponses(filtered, targetDate);

        boolean asc = !"desc".equalsIgnoreCase(order);
        String sortKey = sort == null ? "name" : sort.toLowerCase().trim();
        java.util.Comparator<CohortDto.CohortResponse> cmp;
        switch (sortKey) {
            case "students":
            case "studentcount":
                cmp = java.util.Comparator.comparingInt(CohortDto.CohortResponse::getStudentCount);
                break;
            case "rate":
            case "attendancerate":
                cmp = java.util.Comparator.comparingDouble(CohortDto.CohortResponse::getAttendanceRate);
                break;
            case "createdat":
            case "date":
                cmp = java.util.Comparator.comparing(CohortDto.CohortResponse::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
                break;
            default:
                cmp = java.util.Comparator.comparing(CohortDto.CohortResponse::getName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        responses.sort(asc ? cmp : cmp.reversed());

        int total = responses.size();
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);

        List<CohortDto.CohortResponse> content = responses.subList(from, to);

        return new AnalyticsDto.PageResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    public CohortDto.CohortResponse updateCohort(String actorId, String actorName, String cohortId,
                                                CohortDto.UpdateCohortRequest request) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw AppException.badRequest("Cohort name cannot be empty");
        }

        String newName = request.getName().trim();
        cohortRepository.findByNameIgnoreCase(newName).ifPresent(existing -> {
            if (!existing.getId().equals(cohortId)) {
                throw AppException.badRequest("A cohort with the name '" + newName + "' already exists");
            }
        });

        cohort.setName(newName);
        if (request.getSchedule() != null) cohort.setSchedule(request.getSchedule());
        if (request.getDescription() != null) cohort.setDescription(request.getDescription());

        if (request.getFacilitatorId() != null) {
            String oldFacId = cohort.getFacilitatorId();
            cohort.setFacilitatorId(request.getFacilitatorId());

            userRepository.findById(request.getFacilitatorId()).ifPresent(fac -> {
                if (fac.getAssignedCohortIds() == null) fac.setAssignedCohortIds(new java.util.ArrayList<>());
                if (!fac.getAssignedCohortIds().contains(cohortId)) {
                    fac.getAssignedCohortIds().add(cohortId);
                    userRepository.save(fac);
                }
            });

            if (!request.getFacilitatorId().equals(oldFacId)) {
                auditService.log(actorId, actorName, "SUPER_ADMIN",
                        AuditLog.ActionType.FACILITATOR_REASSIGNED, cohortId, cohort.getName(),
                        "Facilitator reassigned for cohort " + cohort.getName(), null);
            }
        }

        Cohort saved = cohortRepository.save(cohort);
        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.COHORT_UPDATED, cohortId, saved.getName(),
                "Cohort updated: " + saved.getName(), null);

        return toResponse(saved);
    }

    public void deleteCohort(String actorId, String actorName, String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));

        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        for (User s : students) {
            s.setCohortId(null);
        }
        if (!students.isEmpty()) {
            userRepository.saveAll(students);
        }

        cohortRepository.delete(cohort);

        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.COHORT_DELETED, cohortId, cohort.getName(),
                "Cohort deleted: " + cohort.getName(), null);
    }

    public CohortDto.CohortResponse getCohortById(String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        return toResponse(cohort);
    }

    public CohortDto.CohortResponse toggleCohort(String actorId, String actorName, String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        cohort.setActive(!cohort.isActive());
        Cohort saved = cohortRepository.save(cohort);
        auditService.log(actorId, actorName, "SUPER_ADMIN",
                AuditLog.ActionType.COHORT_TOGGLED, cohortId, cohort.getName(),
                "Cohort " + (saved.isActive() ? "activated" : "deactivated"), null);
        return toResponse(saved);
    }

    // ── Dashboard Stats ──────────────────────────────────
    public DashboardDto.AdminStats buildAdminStats() {
        return buildAdminStats(null);
    }

    public DashboardDto.AdminStats buildAdminStats(String cohortId) {
        boolean scoped = cohortId != null && !cohortId.isBlank();
        List<User> students = scoped
                ? userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT)
                : userRepository.findByRole(User.Role.STUDENT);
        long facilitators = userRepository.countByRole(User.Role.FACILITATOR);
        List<Cohort> activeCohorts = cohortRepository.findByActive(true);
        Map<String, String> cohortNameMap = cohortRepository.findAll().stream()
                .collect(Collectors.toMap(Cohort::getId, Cohort::getName, (a, b) -> a));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        // Today's counts (indexed, small)
        List<Attendance> todayAtt = scoped
                ? attendanceRepository.findByCohortIdAndDate(cohortId, today)
                : attendanceRepository.findByDate(today);

        int present = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int holidayToday = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HOLIDAY).count();
        int absent = Math.max(0, students.size() - (present + late + excused));
        double rate = students.size() > 0 ? (double)(present + late + excused) / students.size() * 100 : 0;

        long totalExcusedAllTime = scoped
                ? attendanceRepository.countByCohortIdAndStatus(cohortId, Attendance.AttendanceStatus.EXCUSED)
                : attendanceRepository.countByStatus(Attendance.AttendanceStatus.EXCUSED);

        // Per-student all-time aggregates (single aggregation, no full-collection fetch)
        Map<String, AnalyticsDto.StudentAttendanceStats> statsByStudent = aggregateStudentStats(scoped ? cohortId : null);

        List<DashboardDto.BehaviourInsight> behaviourList = students.stream().map(student -> {
            AnalyticsDto.StudentAttendanceStats s = statsByStudent.get(student.getId());
            long total = s != null ? s.getTotal() : 0;
            long pCount = s != null ? s.getPresent() : 0;
            long lCount = s != null ? s.getLate() : 0;
            long eCount = s != null ? s.getExcused() : 0;
            long aCount = s != null ? s.getAbsent() : 0;

            double sRate = total > 0 ? (double)(pCount + lCount + eCount) / total * 100 : 100.0;
            double lRate = total > 0 ? (double) lCount / total * 100 : 0.0;

            String tag = "GOOD_STANDING";
            String text = "Regular attendance pattern";

            if (eCount >= 2) {
                tag = "HIGH_EXCUSES";
                text = eCount + " approved excuse requests on record";
            } else if (lRate >= 25) {
                tag = "CHRONIC_LATE";
                text = "Frequent late arrival rate (" + Math.round(lRate) + "% late)";
            } else if (sRate < 75 && total > 0) {
                tag = "CHRONIC_ABSENT";
                text = "At-risk attendance rate (" + Math.round(sRate) + "%)";
            } else if (sRate >= 90 && total >= 3) {
                tag = "EXCELLENT";
                text = "Excellent attendance and punctuality record";
            }

            String cName = student.getCohortId() != null ? cohortNameMap.getOrDefault(student.getCohortId(), "Unassigned") : "Unassigned";

            return new DashboardDto.BehaviourInsight(
                    student.getId(), student.getName(), cName, (int) total, (int) pCount, (int) lCount, (int) aCount, (int) eCount,
                    sRate, lRate, tag, text
            );
        }).collect(Collectors.toList());

        Map<String, Map<String, Integer>> dayOfWeekMap = aggregateDayOfWeek(scoped ? cohortId : null);

        List<Map<String, Object>> recentActivity = auditService.getRecent().stream()
                .limit(10).map(l -> Map.<String, Object>of(
                        "action", l.getAction().name(),
                        "actor", l.getActorName(),
                        "detail", l.getDetail() != null ? l.getDetail() : "",
                        "ts", l.getCreatedAt().toString()))
                .collect(Collectors.toList());

        return new DashboardDto.AdminStats(
                students.size(), (int) facilitators, activeCohorts.size(),
                present, late, absent, excused, holidayToday, (int) totalExcusedAllTime, rate,
                toResponses(activeCohorts),
                recentActivity, behaviourList, dayOfWeekMap);
    }

    // ── Aggregations ─────────────────────────────────────

    /**
     * Per-student all-time attendance counts via a single MongoDB aggregation,
     * replacing a full-collection fetch + per-student Java loops.
     */
    private Map<String, AnalyticsDto.StudentAttendanceStats> aggregateStudentStats(String cohortId) {
        List<AggregationOperation> ops = new ArrayList<>();
        if (cohortId != null && !cohortId.isBlank()) {
            ops.add(context -> new Document("$match", new Document("cohortId", cohortId)));
        }
        Document group = new Document("_id", "$studentId")
                .append("total", new Document("$sum", 1))
                .append("present", new Document("$sum", statusCond("PRESENT")))
                .append("late", new Document("$sum", statusCond("LATE")))
                .append("absent", new Document("$sum", statusCond("ABSENT")))
                .append("excused", new Document("$sum", statusCond("EXCUSED")))
                .append("holiday", new Document("$sum", statusCond("HOLIDAY")));
        ops.add(context -> new Document("$group", group));
        ops.add(context -> new Document("$project",
                new Document("_id", 0)
                        .append("studentId", "$_id")
                        .append("total", 1)
                        .append("present", 1)
                        .append("late", 1)
                        .append("absent", 1)
                        .append("excused", 1)
                        .append("holiday", 1)));

        AggregationResults<AnalyticsDto.StudentAttendanceStats> results =
                mongoTemplate.aggregate(Aggregation.newAggregation(ops), "attendance", AnalyticsDto.StudentAttendanceStats.class);
        return results.getMappedResults().stream()
                .collect(Collectors.toMap(AnalyticsDto.StudentAttendanceStats::getStudentId, Function.identity()));
    }

    private static Document statusCond(String status) {
        return new Document("$cond",
                List.of(new Document("$eq", List.of("$status", status)), 1, 0));
    }

    /**
     * PRESENT/LATE/ABSENT/EXCUSED/HOLIDAY counts per weekday via a single
     * aggregation grouped by day-of-week.
     */
    private Map<String, Map<String, Integer>> aggregateDayOfWeek(String cohortId) {
        Map<String, Map<String, Integer>> out = new java.util.LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        for (String day : days) {
            out.put(day, new java.util.HashMap<>(Map.of("PRESENT", 0, "LATE", 0, "ABSENT", 0, "EXCUSED", 0, "HOLIDAY", 0)));
        }

        List<AggregationOperation> ops = new ArrayList<>();
        if (cohortId != null && !cohortId.isBlank()) {
            ops.add(context -> new Document("$match", new Document("cohortId", cohortId)));
        }
        ops.add(context -> new Document("$group",
                new Document("_id", new Document("dow", new Document("$dayOfWeek", "$date"))
                        .append("status", "$status"))
                        .append("count", new Document("$sum", 1))));
        ops.add(context -> new Document("$project",
                new Document("_id", 0).append("dow", "$_id.dow").append("status", "$_id.status").append("count", 1)));
        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), "attendance", Document.class);
        for (Document doc : results.getMappedResults()) {
            int mongoDow = doc.getInteger("dow", 0);
            String status = doc.getString("status");
            int count = doc.getInteger("count", 0);
            String javaDay = mongoDowToJavaDay(mongoDow);
            if (javaDay != null && status != null && out.containsKey(javaDay)) {
                out.get(javaDay).put(status, count);
            }
        }
        return out;
    }

    /** Maps MongoDB $dayOfWeek (1=Sunday..7=Saturday) to the Java weekday name. */
    private static String mongoDowToJavaDay(int mongoDow) {
        if (mongoDow < 1 || mongoDow > 7) return null;
        if (mongoDow == 1) return "SUNDAY";
        return DayOfWeek.of(mongoDow - 1).name();
    }

    public DashboardDto.FacilitatorStats buildFacilitatorStats(String facId) throws Exception {
        return buildFacilitatorStats(facId, null, null, null, 0, 10);
    }

    public DashboardDto.FacilitatorStats buildFacilitatorStats(String facId, String targetCohortId, String queryStr, LocalDate targetDate, int page, int size) throws Exception {
        List<CohortDto.CohortResponse> myCohorts = getCohortsByFacilitator(facId);
        List<String> assignedCohortIds = myCohorts.stream().map(CohortDto.CohortResponse::getId).collect(Collectors.toList());

        List<String> activeCohortIds;
        if (targetCohortId != null && !targetCohortId.isBlank()) {
            if (!assignedCohortIds.contains(targetCohortId)) {
                throw AppException.forbidden("Access denied for cohort " + targetCohortId);
            }
            activeCohortIds = List.of(targetCohortId);
        } else {
            activeCohortIds = assignedCohortIds;
        }

        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        DayOfWeek dow = date.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        List<User> myStudents = activeCohortIds.isEmpty() ? List.of()
                : userRepository.findByCohortIdIn(activeCohortIds);

        String q = queryStr == null ? "" : queryStr.trim().toLowerCase();
        if (!q.isEmpty()) {
            myStudents = myStudents.stream().filter(s ->
                (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                (s.getEmail() != null && s.getEmail().toLowerCase().contains(q)) ||
                (s.getRegistrationNumber() != null && s.getRegistrationNumber().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        int totalStudents = myStudents.size();
        Set<String> studentIds = myStudents.stream().map(User::getId).collect(Collectors.toSet());

        List<Attendance> dateAtt = (studentIds.isEmpty() || isWeekend) ? List.of()
                : attendanceRepository.findByStudentIdIn(studentIds).stream()
                .filter(a -> date.equals(a.getDate()))
                .collect(Collectors.toList());

        Map<String, Attendance> attByStudent = dateAtt.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity(), (a, b) -> a));

        List<ExcuseRequest> excuses = (studentIds.isEmpty() || isWeekend) ? List.of()
                : excuseRepository.findByStudentIdIn(studentIds).stream()
                .filter(e -> e.getStatus() == ExcuseRequest.Status.ACCEPTED || e.getStatus() == ExcuseRequest.Status.APPROVED)
                .filter(e -> e.getStartDate() != null && !date.isBefore(e.getStartDate()) && !date.isAfter(e.getStartDate().plusDays(Math.max(1, e.getNumberOfDays()) - 1)))
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
                } else if (exc != null) {
                    excused++;
                } else {
                    absent++;
                }
            }
        }

        double rate = (totalStudents > 0 && !isWeekend) ? (double) (present + late) / totalStudents * 100.0 : 0.0;

        // Check for active QR
        boolean hasActiveQr = false;
        QrDto.QrResponse activeSession = null;
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

        List<AttendanceDto.AttendanceRecord> allRecords = myStudents.stream().map(s -> {
            Cohort c = s.getCohortId() != null ? cohortsById.get(s.getCohortId()) : null;
            Attendance a = attByStudent.get(s.getId());
            ExcuseRequest exc = excuseByStudent.get(s.getId());

            String status = a != null ? (a.getStatus() != null ? a.getStatus().name() : "ABSENT")
                          : (exc != null ? "EXCUSED" : "ABSENT");

            return new AttendanceDto.AttendanceRecord(
                    a != null ? a.getId() : null,
                    s.getId(),
                    s.getName(),
                    s.getRegistrationNumber(),
                    s.getCohortId(),
                    c != null ? c.getName() : s.getCohortId(),
                    date,
                    a != null ? a.getMarkedAt() : null,
                    status,
                    a != null && a.isManual(),
                    a != null ? a.getManualReason() : null,
                    null
            );
        }).collect(Collectors.toList());

        // Two-tier sorting: Attended (Early/Present/Late) ordered by markedAt ASCENDING (earliest first), Absent/Excused sorted A-Z by full name
        AttendanceService.sortFacilitatorAttendanceRecords(allRecords);

        // Pagination for sorted student records
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, totalStudents);
        int to = Math.min(from + safeSize, totalStudents);

        List<AttendanceDto.AttendanceRecord> pagedRecords = allRecords.subList(from, to);

        AnalyticsDto.PageResponse<AttendanceDto.AttendanceRecord> pageResponse =
                new AnalyticsDto.PageResponse<>(pagedRecords, safePage, safeSize, totalStudents, (int) Math.ceil((double) totalStudents / safeSize));

        return new DashboardDto.FacilitatorStats(totalStudents, present, late, absent, excused, Math.round(rate * 10.0) / 10.0,
                hasActiveQr, isWeekend, activeSession, pagedRecords, pageResponse);
    }

    private static int getStatusPriorityRank(String status) {
        if (status == null) return 5;
        switch (status.toUpperCase()) {
            case "PRESENT": return 1;
            case "LATE":    return 2;
            case "ABSENT":  return 3;
            case "EXCUSED": return 4;
            default:        return 5;
        }
    }

    public DashboardDto.StudentStats buildStudentStats(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        List<Attendance> rawAtt = attendanceRepository.findByStudentId(studentId);
        List<Attendance> allAtt = rawAtt.stream()
                .filter(a -> a.getDate() != null && a.getDate().getDayOfWeek().getValue() < 6)
                .collect(Collectors.toList());
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        int present = (int) allAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) allAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int absent = (int) allAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
        int excused = (int) allAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        double rate = allAtt.size() > 0 ? (double)(present+late)/allAtt.size()*100 : 0;

        java.util.Optional<Attendance> todayRecord = attendanceRepository.findByStudentIdAndDate(studentId, today);
        boolean markedToday = todayRecord.isPresent();
        String todayStatus = markedToday ? todayRecord.get().getStatus().name() : null;

        List<AttendanceDto.AttendanceRecord> recent = allAtt.stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .limit(10)
                .map(a -> new AttendanceDto.AttendanceRecord(
                        a.getId(), a.getStudentId(), student.getName(),
                        student.getRegistrationNumber(),
                        a.getCohortId(), null, a.getDate(), a.getMarkedAt(),
                        a.getStatus() != null ? a.getStatus().name() : null,
                        a.isManual(), a.getManualReason(), null))
                .collect(Collectors.toList());

        Device device = deviceRepository.findByStudentId(studentId).orElse(null);
        DashboardDto.StudentStats.DeviceStatus deviceStatus = device != null
                ? new DashboardDto.StudentStats.DeviceStatus(device.isLocked(), device.getFingerprint(), device.getRegisteredAt())
                : new DashboardDto.StudentStats.DeviceStatus(false, null, null);

        return new DashboardDto.StudentStats(
                allAtt.size(), present, late, absent, excused, rate,
                markedToday, todayStatus, recent, deviceStatus);
    }

    private CohortDto.CohortResponse toResponse(Cohort c) {
        return toResponses(List.of(c)).get(0);
    }
}
