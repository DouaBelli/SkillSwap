package com.skillswap.backend.repository;

import com.skillswap.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUniversityEmail(String universityEmail);
    boolean existsByUniversityEmail(String universityEmail);
    @Query(value = "SELECT COUNT(*) > 0 FROM user_block WHERE user_id = :userId AND unblocked_at IS NULL",
       nativeQuery = true)
boolean isBlocked(@Param("userId") Integer userId);
}