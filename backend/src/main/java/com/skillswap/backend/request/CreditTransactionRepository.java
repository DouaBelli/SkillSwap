package com.skillswap.backend.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Integer> {

    boolean existsByRequestId(Integer requestId);

    /** Total credits of a volunteer = credits of confirmed requests on their offers. */
    @Query(value = """
            SELECT COALESCE(SUM(ct.credits), 0)
            FROM credit_transaction ct
            JOIN service_request sr ON sr.request_id = ct.request_id
            JOIN service_offer so   ON so.offer_id   = sr.offer_id
            WHERE so.volunteer_id = :userId
            """, nativeQuery = true)
    long totalCreditsOf(@Param("userId") Integer userId);
}
