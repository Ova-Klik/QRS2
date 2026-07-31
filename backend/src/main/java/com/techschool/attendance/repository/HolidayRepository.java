package com.techschool.attendance.repository;

import com.techschool.attendance.model.Holiday;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends MongoRepository<Holiday, String> {
    List<Holiday> findByActive(boolean active);

    /** Active holidays whose range overlaps the given date, ordered by start date desc. */
    List<Holiday> findByActiveAndStartDateLessThanEqualOrderByStartDateDesc(boolean active, LocalDate end);

    List<Holiday> findByActiveAndCohortId(boolean active, String cohortId);
}
