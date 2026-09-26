package com.techschool.attendance.service;

import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.Cohort;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.CohortRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.request.CohortRequestDto;
import com.techschool.attendance.dto.response.AnalyticsResponseDto;
import com.techschool.attendance.dto.response.CohortResponseDto;
import com.techschool.attendance.dto.response.DashboardResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CohortService {

    private final CohortRepository cohortRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CohortReadService cohortReadService;
    private final CohortAdminDashboardStatsService adminDashboardStatsService;
    private final CohortFacilitatorDashboardStatsService facilitatorDashboardStatsService;
    private final CohortStudentDashboardStatsService studentDashboardStatsService;

    public CohortResponseDto.CohortResponse createCohort(
            String actorId, String actorName, CohortRequestDto.CreateCohortRequest request) {
        Cohort cohort = new Cohort();
        cohort.setName(request.getName());
        cohort.setFacilitatorId(request.getFacilitatorId());
        cohort.setSchedule(request.getSchedule() != null ? request.getSchedule() : "7:00 AM – 8:30 AM");
        cohort.setDescription(request.getDescription());
        Cohort saved = cohortRepository.save(cohort);

        userRepository.findById(request.getFacilitatorId()).ifPresent(facilitator -> {
            if (facilitator.getAssignedCohortIds() == null) facilitator.setAssignedCohortIds(new ArrayList<>());
            facilitator.getAssignedCohortIds().add(saved.getId());
            userRepository.save(facilitator);
        });

        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.COHORT_CREATED,
                saved.getId(), saved.getName(), "Cohort created: " + saved.getName(), null);
        return cohortReadService.toResponse(saved);
    }

    public List<CohortResponseDto.CohortResponse> getAllCohorts() {
        return cohortReadService.getAllCohorts();
    }

    public List<CohortResponseDto.CohortResponse> getActiveCohorts() {
        return cohortReadService.getActiveCohorts();
    }

    public List<CohortResponseDto.CohortResponse> getCohortsByFacilitator(String facilitatorId) {
        return cohortReadService.getCohortsByFacilitator(facilitatorId);
    }

    public AnalyticsResponseDto.PageResponse<CohortResponseDto.CohortResponse> searchCohorts(
            String query, String status, int page, int size, String sort, String order) {
        return cohortReadService.searchCohorts(query, status, page, size, sort, order);
    }

    public AnalyticsResponseDto.PageResponse<CohortResponseDto.CohortResponse> searchCohorts(
            String query, String status, LocalDate targetDate, String cohortIdFilter,
            int page, int size, String sort, String order) {
        return cohortReadService.searchCohorts(query, status, targetDate, cohortIdFilter, page, size, sort, order);
    }

    public CohortResponseDto.CohortResponse updateCohort(
            String actorId, String actorName, String cohortId, CohortRequestDto.UpdateCohortRequest request) {
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
            String oldFacilitatorId = cohort.getFacilitatorId();
            cohort.setFacilitatorId(request.getFacilitatorId());
            userRepository.findById(request.getFacilitatorId()).ifPresent(facilitator -> {
                if (facilitator.getAssignedCohortIds() == null) facilitator.setAssignedCohortIds(new ArrayList<>());
                if (!facilitator.getAssignedCohortIds().contains(cohortId)) {
                    facilitator.getAssignedCohortIds().add(cohortId);
                    userRepository.save(facilitator);
                }
            });
            if (!request.getFacilitatorId().equals(oldFacilitatorId)) {
                auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.FACILITATOR_REASSIGNED,
                        cohortId, cohort.getName(), "Facilitator reassigned for cohort " + cohort.getName(), null);
            }
        }

        Cohort saved = cohortRepository.save(cohort);
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.COHORT_UPDATED,
                cohortId, saved.getName(), "Cohort updated: " + saved.getName(), null);
        return cohortReadService.toResponse(saved);
    }

    public void deleteCohort(String actorId, String actorName, String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        List<User> students = userRepository.findByCohortIdAndRole(cohortId, User.Role.STUDENT);
        students.forEach(student -> student.setCohortId(null));
        if (!students.isEmpty()) userRepository.saveAll(students);
        cohortRepository.delete(cohort);
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.COHORT_DELETED,
                cohortId, cohort.getName(), "Cohort deleted: " + cohort.getName(), null);
    }

    public CohortResponseDto.CohortResponse getCohortById(String cohortId) {
        return cohortReadService.getCohortById(cohortId);
    }

    public CohortResponseDto.CohortResponse toggleCohort(String actorId, String actorName, String cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));
        cohort.setActive(!cohort.isActive());
        Cohort saved = cohortRepository.save(cohort);
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.COHORT_TOGGLED,
                cohortId, cohort.getName(), "Cohort " + (saved.isActive() ? "activated" : "deactivated"), null);
        return cohortReadService.toResponse(saved);
    }

    public DashboardResponseDto.AdminStats buildAdminStats() {
        return buildAdminStats(null);
    }

    public DashboardResponseDto.AdminStats buildAdminStats(String cohortId) {
        return adminDashboardStatsService.buildAdminStats(cohortId, cohortReadService::toResponses);
    }

    public DashboardResponseDto.FacilitatorStats buildFacilitatorStats(String facilitatorId) throws Exception {
        return buildFacilitatorStats(facilitatorId, null, null, null, 0, 10);
    }

    public DashboardResponseDto.FacilitatorStats buildFacilitatorStats(
            String facilitatorId, String targetCohortId, String query, LocalDate targetDate, int page, int size) throws Exception {
        List<CohortResponseDto.CohortResponse> assigned = cohortReadService.getCohortsByFacilitator(facilitatorId);
        return facilitatorDashboardStatsService.buildFacilitatorStats(
            targetCohortId, query, targetDate, page, size, assigned);
    }

    public DashboardResponseDto.StudentStats buildStudentStats(String studentId) {
        return studentDashboardStatsService.buildStudentStats(studentId);
    }
}
