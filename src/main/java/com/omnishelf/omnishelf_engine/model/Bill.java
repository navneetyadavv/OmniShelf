package com.omnishelf.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bills")
@Data
@NoArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String billNumber;

    // Legacy name field kept for display; FK to Customer for analytics
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private String shopkeeperPhone;

    @Enumerated(EnumType.STRING)
    private BillStatus status;

    private BigDecimal subtotal       = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal taxableAmount  = BigDecimal.ZERO;
    private BigDecimal cgst           = BigDecimal.ZERO;
    private BigDecimal sgst           = BigDecimal.ZERO;
    private BigDecimal grandTotal     = BigDecimal.ZERO;

    // Kept for backward compat — equals cgst+sgst
    private BigDecimal taxAmount      = BigDecimal.ZERO;
    private BigDecimal totalAmount    = BigDecimal.ZERO;

    private LocalDateTime createdAt    = LocalDateTime.now();
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private String        cancelledBy;

    // Correlation / trace ID for structured logging
    @Column(unique = true)
    private String traceId;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillItem> items = new ArrayList<>();
}
