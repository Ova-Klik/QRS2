package com.techschool.attendance.repository;

import com.techschool.attendance.model.Attendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends MongoRepository<Attendance, String> {
    Optional<Attendance> findByStudentIdAndDate(String studentId, LocalDate date);
    List<Attendance> findByStudentId(String studentId);
    List<Attendance> findByCohortIdAndDate(String cohortId, LocalDate date);
    List<Attendance> findByCohortId(String cohortId);
    long countByCohortIdAndDateAndStatus(String cohortId, LocalDate date, Attendance.AttendanceStatus status);
    boolean existsByStudentIdAndDate(String studentId, LocalDate date);
}
