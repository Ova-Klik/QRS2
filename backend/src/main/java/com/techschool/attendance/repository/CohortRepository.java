package com.techschool.attendance.repository;

import com.techschool.attendance.model.Cohort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CohortRepository extends MongoRepository<Cohort, String> {
    List<Cohort> findByFacilitatorId(String facilitatorId);
    List<Cohort> findByActive(boolean active);
}
