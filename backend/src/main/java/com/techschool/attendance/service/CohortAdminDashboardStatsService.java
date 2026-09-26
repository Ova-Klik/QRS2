package com.techschool.attendance.service;

import com.techschool.attendance.data.model.Attendance;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AttendanceRepository;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.CohortResponseDto;
import com.techschool.attendance.dto.response.DashboardResponseDto;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CohortAdminDashboardStatsService {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;
    private final AuditService auditService;
    private final MongoTemplate mongoTemplate;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public CohortAdminDashboardStatsService(
            UserRepository userRepository,
            AttendanceRepository attendanceRepository,
            CohortRepository cohortRepository,
            AuditService auditService,
            MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.cohortRepository = cohortRepository;
        this.auditService = auditService;
        this.mongoTemplate = mongoTemplate;
    }

    public DashboardResponseDto.AdminStats buildAdminStats(
            String cohortId,
            Function<List<Cohort>, List<CohortResponseDto.CohortResponse>> cohortMapper) {
        boolean scoped = cohortId != null && !cohortId.isBlank();
        List<User> students = scoped
                ? userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT)
                : userRepository.findByRole(User.Role.STUDENT);
        long facilitators = userRepository.countByRole(User.Role.FACILITATOR);
        List<Cohort> activeCohorts = cohortRepository.findByActive(true);
        Map<String, String> cohortNameMap = cohortRepository.findAll().stream()
                .collect(Collectors.toMap(Cohort::getId, Cohort::getName, (a, b) -> a));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));
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

        Map<String, AnalyticsResponseDto.StudentAttendanceStats> statsByStudent = aggregateStudentStats(scoped ? cohortId : null);
        List<DashboardResponseDto.BehaviourInsight> behaviourList = students.stream().map(student -> {
            AnalyticsResponseDto.StudentAttendanceStats s = statsByStudent.get(student.getId());
            long total = s != null ? s.getTotal() : 0;
            long pCount = s != null ? s.getPresent() : 0;
            long lCount = s != null ? s.getLate() : 0;
            long eCount = s != null ? s.getExcused() : 0;
            long aCount = s != null ? s.getAbsent() : 0;
            double sRate = total > 0 ? (double)(pCount + lCount + eCount) / total * 100 : 100.0;
            double lRate = total > 0 ? (double) lCount / total * 100 : 0.0;

            DashboardResponseDto.BehaviorTag tag = DashboardResponseDto.BehaviorTag.GOOD_STANDING;
            String text = "Regular attendance pattern";
            if (eCount >= 2) {
                tag = DashboardResponseDto.BehaviorTag.HIGH_EXCUSES;
                text = eCount + " approved excuse requests on record";
            } else if (lRate >= 25) {
                tag = DashboardResponseDto.BehaviorTag.CHRONIC_LATE;
                text = "Frequent late arrival rate (" + Math.round(lRate) + "% late)";
            } else if (sRate < 75 && total > 0) {
                tag = DashboardResponseDto.BehaviorTag.CHRONIC_ABSENT;
                text = "At-risk attendance rate (" + Math.round(sRate) + "%)";
            } else if (sRate >= 90 && total >= 3) {
                tag = DashboardResponseDto.BehaviorTag.EXCELLENT;
                text = "Excellent attendance and punctuality record";
            }
            String cName = student.getCohortId() != null
                    ? cohortNameMap.getOrDefault(student.getCohortId(), "Unassigned") : "Unassigned";
            return new DashboardResponseDto.BehaviourInsight(
                    student.getId(), student.getName(), cName, (int) total, (int) pCount, (int) lCount,
                    (int) aCount, (int) eCount, sRate, lRate, tag, text);
        }).collect(Collectors.toList());

        Map<String, Map<String, Integer>> dayOfWeekMap = aggregateDayOfWeek(scoped ? cohortId : null);
        List<Map<String, Object>> recentActivity = auditService.getRecent().stream()
                .limit(10).map(l -> Map.<String, Object>of(
                        "action", l.getAction().name(),
                        "actor", l.getActorName(),
                        "detail", l.getDetail() != null ? l.getDetail() : "",
                        "ts", l.getCreatedAt().toString()))
                .collect(Collectors.toList());

        return new DashboardResponseDto.AdminStats(
                students.size(), (int) facilitators, activeCohorts.size(),
                present, late, absent, excused, holidayToday, (int) totalExcusedAllTime, rate,
                cohortMapper.apply(activeCohorts), recentActivity, behaviourList, dayOfWeekMap);
    }

    /** Per-student all-time attendance counts via a single MongoDB aggregation. */
    private Map<String, AnalyticsResponseDto.StudentAttendanceStats> aggregateStudentStats(String cohortId) {
        List<AggregationOperation> ops = new ArrayList<>();
        Document matchDoc = new Document();
        if (cohortId != null && !cohortId.isBlank()) matchDoc.append("cohortId", cohortId);
        matchDoc.append("$expr", new Document("$and", List.of(
                new Document("$ne", List.of(new Document("$dayOfWeek", "$date"), 1)),
                new Document("$ne", List.of(new Document("$dayOfWeek", "$date"), 7))
        )));
        ops.add(context -> new Document("$match", matchDoc));
        Document group = new Document("_id", "$studentId")
                .append("total", new Document("$sum", 1))
                .append("present", new Document("$sum", statusCond("PRESENT")))
                .append("late", new Document("$sum", statusCond("LATE")))
                .append("absent", new Document("$sum", statusCond("ABSENT")))
                .append("excused", new Document("$sum", statusCond("EXCUSED")))
                .append("holiday", new Document("$sum", statusCond("HOLIDAY")));
        ops.add(context -> new Document("$group", group));
        ops.add(context -> new Document("$project", new Document("_id", 0)
                .append("studentId", "$_id").append("total", 1).append("present", 1)
                .append("late", 1).append("absent", 1).append("excused", 1).append("holiday", 1)));

        AggregationResults<AnalyticsResponseDto.StudentAttendanceStats> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), "attendance", AnalyticsResponseDto.StudentAttendanceStats.class);
        return results.getMappedResults().stream().collect(Collectors.toMap(
                AnalyticsResponseDto.StudentAttendanceStats::getStudentId, Function.identity()));
    }

    private static Document statusCond(String status) {
        return new Document("$cond", List.of(new Document("$eq", List.of("$status", status)), 1, 0));
    }

    /** PRESENT/LATE/ABSENT/EXCUSED/HOLIDAY counts per weekday via one aggregation. */
    private Map<String, Map<String, Integer>> aggregateDayOfWeek(String cohortId) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        for (String day : days) {
            out.put(day, new java.util.HashMap<>(Map.of(
                    "PRESENT", 0, "LATE", 0, "ABSENT", 0, "EXCUSED", 0, "HOLIDAY", 0)));
        }
        List<AggregationOperation> ops = new ArrayList<>();
        if (cohortId != null && !cohortId.isBlank()) {
            ops.add(context -> new Document("$match", new Document("cohortId", cohortId)));
        }
        ops.add(context -> new Document("$group", new Document("_id",
                new Document("dow", new Document("$dayOfWeek", "$date")).append("status", "$status"))
                .append("count", new Document("$sum", 1))));
        ops.add(context -> new Document("$project", new Document("_id", 0)
                .append("dow", "$_id.dow").append("status", "$_id.status").append("count", 1)));
        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), "attendance", Document.class);
        for (Document doc : results.getMappedResults()) {
            int mongoDow = doc.getInteger("dow", 0);
            String status = doc.getString("status");
            int count = doc.getInteger("count", 0);
            String javaDay = mongoDowToJavaDay(mongoDow);
            if (javaDay != null && status != null && out.containsKey(javaDay)) out.get(javaDay).put(status, count);
        }
        return out;
    }

    /** Maps MongoDB $dayOfWeek (1=Sunday..7=Saturday) to the Java weekday name. */
    private static String mongoDowToJavaDay(int mongoDow) {
        if (mongoDow < 1 || mongoDow > 7) return null;
        if (mongoDow == 1) return "SUNDAY";
        return DayOfWeek.of(mongoDow - 1).name();
    }
}