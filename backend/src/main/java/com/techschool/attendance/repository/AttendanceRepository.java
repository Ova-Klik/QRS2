package com.techschool.attendance.repository;

import com.techschool.attendance.model.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends MongoRepository<Attendance, String> {
    Optional<Attendance> findByStudentIdAndDate(String studentId, LocalDate date);
    List<Attendance> findByStudentId(String studentId);
    Page<Attendance> findByStudentId(String studentId, Pageable pageable);
    List<Attendance> findByStudentIdIn(Collection<String> studentIds);
    List<Attendance> findByStudentIdOrderByDateAsc(String studentId);
    List<Attendance> findByCohortIdAndDate(String cohortId, LocalDate date);
    List<Attendance> findByCohortId(String cohortId);
    List<Attendance> findByCohortIdIn(Collection<String> cohortIds);
    List<Attendance> findByDate(LocalDate date);

    @Query("{'cohortId': ?0, 'date': {'$gte': ?1, '$lte': ?2}}")
    List<Attendance> findByCohortIdAndDateBetween(String cohortId, LocalDate start, LocalDate end);

    @Query("{'date': {'$gte': ?0, '$lte': ?1}}")
    List<Attendance> findByDateBetween(LocalDate start, LocalDate end);

    @Query("{'studentId': ?0, 'date': {'$gte': ?1, '$lte': ?2}}")
    List<Attendance> findByStudentIdAndDateBetween(String studentId, LocalDate start, LocalDate end);

    @Query("{'studentId': {'$in': ?0}, 'date': {'$gte': ?1, '$lte': ?2}}")
    List<Attendance> findByStudentIdInAndDateBetween(Collection<String> studentIds, LocalDate start, LocalDate end);

    @Query("{'cohortId': ?0, 'date': {'$gte': ?1, '$lte': ?2}}")
    Page<Attendance> findByCohortIdAndDateBetween(String cohortId, LocalDate start, LocalDate end, Pageable pageable);

    @Query("{'date': {'$gte': ?0, '$lte': ?1}}")
    Page<Attendance> findByDateBetween(LocalDate start, LocalDate end, Pageable pageable);
    long countByCohortIdAndDateAndStatus(String cohortId, LocalDate date, Attendance.AttendanceStatus status);
    long countByStatus(Attendance.AttendanceStatus status);
    long countByCohortIdAndStatus(String cohortId, Attendance.AttendanceStatus status);
    boolean existsByStudentIdAndDate(String studentId, LocalDate date);
}
