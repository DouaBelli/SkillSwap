package com.skillswap.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "email_verification")
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "purpose", nullable = false, length = 30)
    private String purpose; // REGISTRATION or PASSWORD_RESET

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    // expires_at and created_at are filled by the database, never by Java
    @Column(name = "expires_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private Short attempts = 0;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public Integer getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Short getAttempts() { return attempts; }
    public void setAttempts(Short attempts) { this.attempts = attempts; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}