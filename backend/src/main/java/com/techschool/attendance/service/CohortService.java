package com.techschool.attendance.service;

import com.techschool.attendance.dto.*;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Map;
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
        return cohortRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CohortDto.CohortResponse> getActiveCohorts() {
        return cohortRepository.findByActive(true).stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CohortDto.CohortResponse> getCohortsByFacilitator(String facId) {
        return cohortRepository.findByFacilitatorId(facId).stream()
                .map(this::toResponse).collect(Collectors.toList());
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
        List<User> students = userRepository.findByRole(User.Role.STUDENT);
        List<User> facilitators = userRepository.findByRole(User.Role.FACILITATOR);
        List<Cohort> activeCohorts = cohortRepository.findByActive(true);
        Map<String, String> cohortNameMap = cohortRepository.findAll().stream()
                .collect(Collectors.toMap(Cohort::getId, Cohort::getName, (a, b) -> a));

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> allAtt = attendanceRepository.findAll();

        List<Attendance> todayAtt = allAtt.stream()
                .filter(a -> today.equals(a.getDate())).collect(Collectors.toList());

        int present = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int absent = Math.max(0, students.size() - (present + late + excused));
        double rate = students.size() > 0 ? (double)(present + late + excused) / students.size() * 100 : 0;

        int totalExcusedAllTime = (int) allAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();

        // ── Student Behaviour Analytics ────────────────────────
        List<DashboardDto.BehaviourInsight> behaviourList = students.stream().map(student -> {
            List<Attendance> sAtt = allAtt.stream()
                    .filter(a -> student.getId().equals(a.getStudentId())).collect(Collectors.toList());
            int total = sAtt.size();
            int pCount = (int) sAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
            int lCount = (int) sAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
            int eCount = (int) sAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
            int aCount = (int) sAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();

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
                    student.getId(), student.getName(), cName, total, pCount, lCount, aCount, eCount,
                    sRate, lRate, tag, text
            );
        }).collect(Collectors.toList());

        // ── Day of Week Breakdown ──────────────────────────────
        Map<String, Map<String, Integer>> dayOfWeekMap = new java.util.HashMap<>();
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        for (String day : days) {
            dayOfWeekMap.put(day, new java.util.HashMap<>(Map.of("PRESENT", 0, "LATE", 0, "ABSENT", 0, "EXCUSED", 0)));
        }

        for (Attendance a : allAtt) {
            if (a.getDate() != null) {
                String dayName = a.getDate().getDayOfWeek().name();
                if (dayOfWeekMap.containsKey(dayName)) {
                    Map<String, Integer> counts = dayOfWeekMap.get(dayName);
                    String st = a.getStatus() != null ? a.getStatus().name() : "ABSENT";
                    counts.put(st, counts.getOrDefault(st, 0) + 1);
                }
            }
        }

        List<Map<String, Object>> recentActivity = auditService.getRecent().stream()
                .limit(10).map(l -> Map.<String, Object>of(
                        "action", l.getAction().name(),
                        "actor", l.getActorName(),
                        "detail", l.getDetail() != null ? l.getDetail() : "",
                        "ts", l.getCreatedAt().toString()))
                .collect(Collectors.toList());

        return new DashboardDto.AdminStats(
                students.size(), facilitators.size(), activeCohorts.size(),
                present, late, absent, excused, totalExcusedAllTime, rate,
                activeCohorts.stream().map(this::toResponse).collect(Collectors.toList()),
                recentActivity, behaviourList, dayOfWeekMap);
    }

    public DashboardDto.FacilitatorStats buildFacilitatorStats(String facId) throws Exception {
        List<Cohort> myCohorts = cohortRepository.findByFacilitatorId(facId);
        List<User> myStudents = myCohorts.stream()
                .flatMap(c -> userRepository.findByCohortId(c.getId()).stream())
                .collect(Collectors.toList());
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        List<Attendance> todayAtt = myStudents.stream()
                .flatMap(s -> attendanceRepository.findByStudentIdAndDate(s.getId(), today).stream())
                .collect(Collectors.toList());

        int present = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        int late = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE).count();
        int excused = (int) todayAtt.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        int absent = Math.max(0, myStudents.size() - (present + late + excused));

        // Check for active QR
        boolean hasActiveQr = false;
        QrDto.QrResponse activeSession = null;
        for (Cohort c : myCohorts) {
            try {
                activeSession = null; // skip QR image regeneration for dashboard
                hasActiveQr = qrSessionRepository.findActiveSessionByCohortId(c.getId()).isPresent();
                if (hasActiveQr) break;
            } catch (Exception ignored) {}
        }

        List<AttendanceDto.AttendanceRecord> records = todayAtt.stream()
                .map(a -> {
                    User s = userRepository.findById(a.getStudentId()).orElse(null);
                    Cohort co = a.getCohortId() != null ? cohortRepository.findById(a.getCohortId()).orElse(null) : null;
                    return new AttendanceDto.AttendanceRecord(
                            a.getId(), a.getStudentId(), s != null ? s.getName() : "",
                            a.getCohortId(), co != null ? co.getName() : "",
                            a.getDate(), a.getMarkedAt(), a.getStatus() != null ? a.getStatus().name() : null,
                            a.isManual(), a.getManualReason());
                }).collect(Collectors.toList());

        return new DashboardDto.FacilitatorStats(myStudents.size(), present, late, absent, excused,
                hasActiveQr, activeSession, records);
    }

    public DashboardDto.StudentStats buildStudentStats(String studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));
        List<Attendance> allAtt = attendanceRepository.findByStudentId(studentId);
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
                        a.getCohortId(), null, a.getDate(), a.getMarkedAt(),
                        a.getStatus() != null ? a.getStatus().name() : null,
                        a.isManual(), a.getManualReason()))
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
        User fac = userRepository.findById(c.getFacilitatorId()).orElse(null);
        int count = userRepository.findByCohortId(c.getId()).size();
        List<Attendance> att = attendanceRepository.findByCohortId(c.getId());
        double rate = count > 0 ? (double) att.stream().filter(a -> a.getStatus() != Attendance.AttendanceStatus.ABSENT).count() / count * 100 : 0;
        return new CohortDto.CohortResponse(c.getId(), c.getName(), c.getFacilitatorId(),
                fac != null ? fac.getName() : null, c.getSchedule(), c.isActive(), count, rate, c.getCreatedAt());
    }
}
