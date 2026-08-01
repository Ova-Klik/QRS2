package com.techschool.attendance.repository;

import com.techschool.attendance.model.QrSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrSessionRepository extends MongoRepository<QrSession, String> {
    Optional<QrSession> findByToken(String token);
    Optional<QrSession> findByCohortIdAndDate(String cohortId, LocalDate date);
    List<QrSession> findByCohortIdOrderByCreatedAtDesc(String cohortId);
    List<QrSession> findByStateAndExpiresAtBefore(QrSession.SessionState state, Instant now);

    @Query("{ 'cohortId': ?0, 'state': 'ACTIVE' }")
    Optional<QrSession> findActiveSessionByCohortId(String cohortId);

    @Query("{ 'cohortId': { $in: ?0 }, 'state': 'ACTIVE' }")
    List<QrSession> findActiveSessionsByCohortIds(List<String> cohortIds);
}
