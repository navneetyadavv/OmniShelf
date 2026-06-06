package com.omnishelf.engine.model;

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

    @Column(nullable = false, unique = true)
    private String shopkeeperPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionState state;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @Column
    private String lastUndoItemId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    @Column
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(20);

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void refreshExpiry(int timeoutMinutes) {
        this.lastActivityAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(timeoutMinutes);
    }
}
