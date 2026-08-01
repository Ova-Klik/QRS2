package com.techschool.attendance.repository;

import com.techschool.attendance.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends MongoRepository<Device, String> {
    Optional<Device> findByStudentId(String studentId);
    List<Device> findByStudentIdIn(Collection<String> studentIds);
    Optional<Device> findByFingerprint(String fingerprint);
    List<Device> findByLocked(boolean locked);
}
