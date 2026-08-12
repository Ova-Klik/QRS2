package com.techschool.attendance.repository;

import com.techschool.attendance.model.ExcuseRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExcuseRequestRepository extends MongoRepository<ExcuseRequest, String> {
    List<ExcuseRequest> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<ExcuseRequest> findByCohortIdOrderByCreatedAtDesc(String cohortId);
    List<ExcuseRequest> findByCohortIdAndStatus(String cohortId, ExcuseRequest.Status status);
    List<ExcuseRequest> findByStudentIdIn(java.util.Collection<String> studentIds);

    @Query("{'status': {'$in': ['ACCEPTED', 'APPROVED']}, 'startDate': {'$lte': ?0}}")
    List<ExcuseRequest> findActiveExcusesOnOrBefore(LocalDate date);
}
