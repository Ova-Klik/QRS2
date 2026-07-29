package com.techschool.attendance.service;

import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

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
}
