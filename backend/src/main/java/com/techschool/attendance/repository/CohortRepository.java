package com.techschool.attendance.repository;

import com.techschool.attendance.model.Cohort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CohortRepository extends MongoRepository<Cohort, String> {
    List<Cohort> findByFacilitatorId(String facilitatorId);
    List<Cohort> findByActive(boolean active);
    Optional<Cohort> findByNameIgnoreCase(String name);
}
