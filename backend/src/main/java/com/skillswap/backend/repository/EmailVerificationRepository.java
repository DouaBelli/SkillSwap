package com.skillswap.backend.repository;

import com.skillswap.backend.entity.EmailVerification;
import com.skillswap.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Integer> {
    Optional<EmailVerification> findFirstByUserAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            User user, String purpose);
}