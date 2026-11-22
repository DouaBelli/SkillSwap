package com.skillswap.backend.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Integer> {

    /**
     * Loads a request and locks its row until the transaction ends, so two clicks
     * (for example a double Confirm) cannot both change the same request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> findByIdForUpdate(@Param("id") Integer id);

    /** Duplicate check: does this user already have an open request for this offer? */
    boolean existsByOfferIdAndRequesterIdAndStatusIn(Integer offerId, Integer requesterId,
                                                     Collection<RequestStatus> statuses);
}
