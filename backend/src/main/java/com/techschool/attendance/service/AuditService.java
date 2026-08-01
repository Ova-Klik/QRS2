package com.techschool.attendance.service;

import com.techschool.attendance.dto.AnalyticsDto;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final MongoTemplate mongoTemplate;

    public void log(String actorId, String actorName, String actorRole,
                    AuditLog.ActionType action, String targetId, String targetName,
                    String detail, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorName(actorName);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setDetail(detail);
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);
    }

    public void logWithMeta(String actorId, String actorName, String actorRole,
                            AuditLog.ActionType action, String targetId, String targetName,
                            String detail, String ipAddress, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorName(actorName);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setDetail(detail);
        log.setIpAddress(ipAddress);
        log.setMetadata(metadata);
        auditLogRepository.save(log);
    }

    public List<AuditLog> getRecent() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public List<AuditLog> getByActor(String actorId) {
        return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId);
    }

    public List<AuditLog> getByTarget(String targetId) {
        return auditLogRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
    }

    /** Filterable, paginated audit trail. Dates are yyyy-MM-dd (UTC). */
    public AnalyticsDto.PageResponse<AuditLog> getLogs(String action, String actorName, String detail,
                                                       String from, String to, int page, int size,
                                                       String sort, String order) {
        Query query = new Query();
        if (action != null && !action.isBlank()) {
            query.addCriteria(Criteria.where("action").is(action.trim().toUpperCase()));
        }
        if (actorName != null && !actorName.isBlank()) {
            query.addCriteria(Criteria.where("actorName").regex(Pattern.quote(actorName.trim()), "i"));
        }
        if (detail != null && !detail.isBlank()) {
            query.addCriteria(Criteria.where("detail").regex(Pattern.quote(detail.trim()), "i"));
        }
        if (from != null && !from.isBlank() || to != null && !to.isBlank()) {
            Criteria range = Criteria.where("createdAt");
            if (from != null && !from.isBlank()) range.gte(parseStartOfDay(from));
            if (to != null && !to.isBlank()) range.lte(parseEndOfDay(to));
            query.addCriteria(range);
        }

        long total = mongoTemplate.count(query, AuditLog.class);
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(0, page);
        boolean asc = "asc".equalsIgnoreCase(order);
        String sortField = switch (sort == null ? "createdAt" : sort) {
            case "action", "actorName", "targetName" -> sort;
            default -> "createdAt";
        };
        query.with(Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, sortField));
        query.skip((long) safePage * safeSize).limit(safeSize);

        List<AuditLog> logs = mongoTemplate.find(query, AuditLog.class);
        return new AnalyticsDto.PageResponse<>(logs, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    private Instant parseStartOfDay(String date) {
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant parseEndOfDay(String date) {
        return LocalDate.parse(date).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
