package com.techschool.attendance.repository;

import com.techschool.attendance.model.SystemSetting;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SystemSettingRepository extends MongoRepository<SystemSetting, String> {
    Optional<SystemSetting> findByKey(String key);
}
