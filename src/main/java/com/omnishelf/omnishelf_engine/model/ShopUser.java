package com.omnishelf.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_users")
@Data
@NoArgsConstructor
public class ShopUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    // ── OTP state ─────────────────────────────────────────────────
    @Column
    private boolean verifiedToday = false;

    @Column
    private LocalDateTime verifiedAt;

    // Brute-force protection: track consecutive wrong OTP attempts
    @Column(nullable = false)
    private int failedOtpAttempts = 0;

    @Column
    private LocalDateTime otpLockedUntil;

    // ── Rate limiting ─────────────────────────────────────────────
    @Column(nullable = false)
    private int billsGeneratedToday = 0;

    @Column
    private LocalDateTime lastBillAt;

    @Column(nullable = false)
    private boolean locked = false;

    @Column
    private LocalDateTime lockedAt;

    @Column
    private String lockReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Helpers ───────────────────────────────────────────────────

    public boolean isVerifiedToday() {
        return verifiedToday && verifiedAt != null
            && verifiedAt.toLocalDate().equals(LocalDate.now());
    }

    public boolean isOtpBruteForced() {
        return otpLockedUntil != null && LocalDateTime.now().isBefore(otpLockedUntil);
    }

    public void incrementFailedOtp(int maxAttempts, int lockoutMinutes) {
        this.failedOtpAttempts++;
        if (this.failedOtpAttempts >= maxAttempts) {
            this.otpLockedUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
            this.failedOtpAttempts = 0;
        }
    }

    public void resetOtpFailures() {
        this.failedOtpAttempts = 0;
        this.otpLockedUntil = null;
    }

    public boolean isOwner() { return role == UserRole.OWNER; }
    public boolean isStaff() { return role == UserRole.STAFF; }
}
