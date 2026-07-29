package com.techschool.attendance.service;

import com.techschool.attendance.dto.ExcuseDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.Attendance;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.model.ExcuseRequest;
import com.techschool.attendance.model.User;
import com.techschool.attendance.repository.AttendanceRepository;
import com.techschool.attendance.repository.ExcuseRequestRepository;
import com.techschool.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcuseService {

    private final ExcuseRequestRepository excuseRequestRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final AuditService auditService;

    public ExcuseDto.Response submitRequest(String studentId, ExcuseDto.CreateRequest request) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student not found"));

        LocalDate start = request.getStartDate();
        LocalDate end = start.plusDays(Math.max(1, request.getNumberOfDays()) - 1);

        ExcuseRequest excuse = new ExcuseRequest();
        excuse.setStudentId(studentId);
        excuse.setStudentName(student.getName());
        excuse.setCohortId(student.getCohortId());
        excuse.setReason(request.getReason());
        excuse.setNumberOfDays(request.getNumberOfDays());
        excuse.setStartDate(start);
        excuse.setEndDate(end);
        excuse.setCoverUpPlan(request.getCoverUpPlan());
        excuse.setStatus(ExcuseRequest.Status.PENDING);
        excuse.setCreatedAt(Instant.now());

        ExcuseRequest saved = excuseRequestRepository.save(excuse);

        auditService.log(studentId, student.getName(), "STUDENT",
                AuditLog.ActionType.EXCUSE_SUBMITTED, saved.getId(), student.getName(),
                "Submitted excuse request for " + request.getNumberOfDays() + " days starting " + start, null);

        return toResponse(saved);
    }

    public ExcuseDto.Response reviewRequest(String actorId, String actorName, String actorRole,
                                             String requestId, ExcuseDto.ReviewRequest request) {
        ExcuseRequest excuse = excuseRequestRepository.findById(requestId)
                .orElseThrow(() -> AppException.notFound("Excuse request not found"));

        excuse.setStatus(request.getStatus());
        excuse.setReviewedById(actorId);
        excuse.setReviewedByName(actorName);
        excuse.setReviewerNotes(request.getNotes());
        excuse.setReviewedAt(Instant.now());

        ExcuseRequest saved = excuseRequestRepository.save(excuse);

        // If accepted/approved, automatically create/update EXCUSED attendance records for the dates in range
        if (request.getStatus() == ExcuseRequest.Status.ACCEPTED || request.getStatus() == ExcuseRequest.Status.APPROVED) {
            LocalDate current = excuse.getStartDate();
            LocalDate end = excuse.getEndDate();
            while (!current.isAfter(end)) {
                Attendance att = attendanceRepository.findByStudentIdAndDate(excuse.getStudentId(), current)
                        .orElse(new Attendance());
                att.setStudentId(excuse.getStudentId());
                att.setCohortId(excuse.getCohortId());
                att.setDate(current);
                att.setMarkedAt(Instant.now());
                att.setStatus(Attendance.AttendanceStatus.EXCUSED);
                att.setManual(true);
                att.setManualReason("Excuse Accepted (" + excuse.getNumberOfDays() + " days): " + excuse.getReason());
                att.setMarkedById(actorId);
                attendanceRepository.save(att);

                current = current.plusDays(1);
            }
        }

        auditService.log(actorId, actorName, actorRole,
                AuditLog.ActionType.EXCUSE_REVIEWED, saved.getId(), excuse.getStudentName(),
                "Excuse request " + request.getStatus() + " — " + request.getNotes(), null);

        return toResponse(saved);
    }

    public List<ExcuseDto.Response> getStudentRequests(String studentId) {
        return excuseRequestRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<ExcuseDto.Response> getCohortRequests(String cohortId) {
        return excuseRequestRepository.findByCohortIdOrderByCreatedAtDesc(cohortId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private ExcuseDto.Response toResponse(ExcuseRequest e) {
        return new ExcuseDto.Response(
                e.getId(), e.getStudentId(), e.getStudentName(),
                e.getCohortId(), e.getReason(), e.getNumberOfDays(),
                e.getStartDate(), e.getEndDate(), e.getCoverUpPlan(),
                e.getStatus(), e.getReviewedById(), e.getReviewedByName(),
                e.getReviewerNotes(), e.getReviewedAt(), e.getCreatedAt()
        );
    }
}
