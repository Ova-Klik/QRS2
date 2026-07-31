package com.techschool.attendance.repository;

import com.techschool.attendance.model.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends MongoRepository<Attendance, String> {
    Optional<Attendance> findByStudentIdAndDate(String studentId, LocalDate date);
    List<Attendance> findByStudentId(String studentId);
    List<Attendance> findByStudentIdOrderByDateAsc(String studentId);
    List<Attendance> findByCohortIdAndDate(String cohortId, LocalDate date);
    List<Attendance> findByCohortId(String cohortId);
    List<Attendance> findByDate(LocalDate date);
    @Query("{'date': {'$gte': ?1, '$lte': ?2}}")
    List<Attendance> findByCohortIdAndDateBetween(String cohortId, LocalDate start, LocalDate end);

    @Query("{'date': {'$gte': ?0, '$lte': ?1}}")
    List<Attendance> findByDateBetween(LocalDate start, LocalDate end);

    @Query("{'date': {'$gte': ?1, '$lte': ?2}}")
    List<Attendance> findByStudentIdAndDateBetween(String studentId, LocalDate start, LocalDate end);

    @Query("{'date': {'$gte': ?1, '$lte': ?2}}")
    Page<Attendance> findByCohortIdAndDateBetween(String cohortId, LocalDate start, LocalDate end, Pageable pageable);

    @Query("{'date': {'$gte': ?0, '$lte': ?1}}")
    Page<Attendance> findByDateBetween(LocalDate start, LocalDate end, Pageable pageable);
    long countByCohortIdAndDateAndStatus(String cohortId, LocalDate date, Attendance.AttendanceStatus status);
    boolean existsByStudentIdAndDate(String studentId, LocalDate date);
}
