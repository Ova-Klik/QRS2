package com.techschool.attendance.repository;

import com.techschool.attendance.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(User.Role role);
    List<User> findByCohortId(String cohortId);
    boolean existsByEmail(String email);
}
