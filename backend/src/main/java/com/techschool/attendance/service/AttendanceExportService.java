package com.techschool.attendance.service;

import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.User;
import com.techschool.attendance.data.repository.AuditLogRepository;
import com.techschool.attendance.data.repository.UserRepository;
import com.techschool.attendance.dto.response.AttendanceResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceExportService {

    private static final List<String> HEADERS = List.of(
            "Student Name", "Cohort", "Attendance Status", "Attendance Date",
            "Attendance Time", "Registration Number", "Excuse Status");

    private final AttendanceFacilitatorReportService attendanceFacilitatorReportService;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public ResponseEntity<byte[]> exportPublicProjectionReport(
            String cohortId, LocalDate targetDate, String format, String clientIp, ExportService exportService) {
        if (cohortId == null || cohortId.isBlank()) {
            throw AppException.badRequest("Cohort ID is required");
        }
        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        return exportFacilitatorReport("PUBLIC_PROJECTION", List.of(cohortId), cohortId,
                null, date, null, format, "projection", exportService);
    }

    public ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, LocalDate targetDate,
            String format, ExportService exportService) {
        return exportFacilitatorReport(null, assignedCohortIds, cohortId,
                null, targetDate, null, format, null, exportService);
    }

    public ResponseEntity<byte[]> exportFacilitatorReport(
            List<String> assignedCohortIds, String cohortId, String queryStr,
            LocalDate targetDate, String statusFilter, String format, ExportService exportService) {
        return exportFacilitatorReport(null, assignedCohortIds, cohortId,
                queryStr, targetDate, statusFilter, format, null, exportService);
    }

    public ResponseEntity<byte[]> exportFacilitatorReport(
            String actorId, List<String> assignedCohortIds, String cohortId, String queryStr,
            LocalDate targetDate, String statusFilter, String format, String source,
            ExportService exportService) {
        boolean projection = source != null && ("projection".equalsIgnoreCase(source.trim())
                || "projection_screen".equalsIgnoreCase(source.trim()));
        if (projection) {
            enforceProjectionDownloadLimit(actorId, cohortId);
        }

        LocalDate date = targetDate != null ? targetDate : LocalDate.now(ZoneId.of(timezone));
        List<AttendanceResponseDto.AttendanceRecord> records =
                attendanceFacilitatorReportService.getFacilitatorReportRecords(
                        assignedCohortIds, cohortId, queryStr, date, statusFilter);
        List<List<Object>> rows = toExportRows(records, date);
        String exportFormat = format != null && !format.isBlank() ? format : "xlsx";
        ResponseEntity<byte[]> response = exportService.export(
                HEADERS, rows, exportFormat, "attendance_report_" + date);

        if (projection && response.getStatusCode().is2xxSuccessful()) {
            User actor = userRepository.findById(actorId).orElse(null);
            auditService.log(actorId, actor != null ? actor.getName() : actorId,
                    actor != null ? actor.getRole().name() : "FACILITATOR",
                    AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, cohortId, cohortId,
                    "Projection Screen report download for cohort " + cohortId, null);
        }
        return response;
    }

    private void enforceProjectionDownloadLimit(String actorId, String cohortId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        ZoneId zone = ZoneId.of(timezone);
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant();

        long countByActor = actorId != null && !"PUBLIC_PROJECTION".equals(actorId)
                ? auditLogRepository.countByActorIdAndActionAndCreatedAtBetween(
                        actorId, AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, startOfDay, endOfDay)
                : 0;
        long countByCohort = cohortId != null && !cohortId.isBlank()
                ? auditLogRepository.countByTargetIdAndActionAndCreatedAtBetween(
                        cohortId, AuditLog.ActionType.PROJECTION_REPORT_DOWNLOADED, startOfDay, endOfDay)
                : 0;

        if (countByActor >= 3 || countByCohort >= 3) {
            throw AppException.badRequest(
                    "You have reached today's Projection Screen download limit (3 downloads). Please try again tomorrow.");
        }
    }

    private List<List<Object>> toExportRows(List<AttendanceResponseDto.AttendanceRecord> records, LocalDate date) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of(timezone));
        List<List<Object>> rows = new ArrayList<>(records.size());
        for (AttendanceResponseDto.AttendanceRecord record : records) {
            String checkInTime = record.getMarkedAt() != null ? timeFormatter.format(record.getMarkedAt()) : "—";
            rows.add(List.of(
                    record.getStudentName() != null ? record.getStudentName() : "",
                    record.getCohortName() != null ? record.getCohortName() : "",
                    record.getStatus() != null ? record.getStatus() : "",
                    date.toString(),
                    checkInTime,
                    record.getRegistrationNumber() != null ? record.getRegistrationNumber() : "",
                    record.getManualReason() != null ? record.getManualReason() : "N/A"));
        }
        return rows;
    }
}
