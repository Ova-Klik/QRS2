package com.techschool.attendance.data.repository;

import com.techschool.attendance.data.model.NetworkSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NetworkSettingsRepository extends MongoRepository<NetworkSettings, String> {
}
