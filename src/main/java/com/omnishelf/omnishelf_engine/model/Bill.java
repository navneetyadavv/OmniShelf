package com.omnishelf.omnishelf_engine.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

    private String billNumber;          // human-readable: BILL-20240115-001
    private String customerName;
    private String shopkeeperPhone;     // WhatsApp number that created this bill

    @Enumerated(EnumType.STRING)
    private BillStatus status;          // DRAFT, CONFIRMED, CANCELLED

    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal grandTotal;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime confirmedAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL)
    private List<BillItem> items = new ArrayList<>();
}
