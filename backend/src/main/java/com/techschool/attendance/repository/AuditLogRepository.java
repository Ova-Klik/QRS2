package com.techschool.attendance.repository;

import com.techschool.attendance.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findTop50ByOrderByCreatedAtDesc();
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(String actorId);
    List<AuditLog> findByTargetIdOrderByCreatedAtDesc(String targetId);
}