package com.omnishelf.omnishelf_engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bill_sessions")
@Data
@NoArgsConstructor
public class BillSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // One session per phone number at a time
    @Column(nullable = false, unique = true)
    private String shopkeeperPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionState state;         // ACTIVE | AWAITING_CONFIRMATION | CONFIRMED | CANCELLED

    // The draft bill this session is building
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    // Undo buffer — stores the last BillItem ID removed for one-step undo
    @Column
    private String lastUndoItemId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    // Sessions idle for 30 minutes auto-expire
    @Column
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void refreshExpiry() {
        this.lastActivityAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(30);
    }
}