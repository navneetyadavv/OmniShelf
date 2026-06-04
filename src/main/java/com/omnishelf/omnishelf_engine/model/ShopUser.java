package com.billing.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_users")
@Data
@NoArgsConstructor
public class ShopUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // WhatsApp number — the identity key for this system
    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;              // OWNER | STAFF

    @Column(nullable = false)
    private boolean active = true;

    // OTP state
    @Column
    private String pendingOtp;          // bcrypt-hashed OTP
    @Column
    private LocalDateTime otpExpiresAt;
    @Column
    private boolean verifiedToday = false;
    @Column
    private LocalDateTime verifiedAt;

    // Rate limiting state
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

    public boolean isOtpValid(LocalDateTime now) {
        return otpExpiresAt != null && now.isBefore(otpExpiresAt);
    }

    public boolean isOwner() { return role == UserRole.OWNER; }
    public boolean isStaff() { return role == UserRole.STAFF; }
}