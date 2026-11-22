package com.skillswap.backend.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;


@Entity
@Table(name = "credit_transaction")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer id;

    @Column(name = "request_id", nullable = false, unique = true)
    private Integer requestId;

    @Column(name = "credits", nullable = false)
    private Integer credits = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CreditTransaction() {
        // for JPA
    }

    public CreditTransaction(Integer requestId) {
        this.requestId = requestId;
        this.credits = 1;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Integer getId() { return id; }
    public Integer getRequestId() { return requestId; }
    public Integer getCredits() { return credits; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
