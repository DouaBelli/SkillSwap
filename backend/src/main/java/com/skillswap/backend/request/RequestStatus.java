package com.skillswap.backend.request;

/**
 * PENDING -> ACCEPTED -> PERFORMED -> CONFIRMED
 * PENDING -> REJECTED
 * PENDING / ACCEPTED / PERFORMED -> CANCELLED
 */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    PERFORMED,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    /** A request is open while it can still move forward (blocks duplicates, can be cancelled). */
    public boolean isOpen() {
        return this == PENDING || this == ACCEPTED || this == PERFORMED;
    }
}
