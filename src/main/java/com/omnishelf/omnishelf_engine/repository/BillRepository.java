package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.Bill;
import com.omnishelf.engine.model.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, String> {

    Optional<Bill> findByBillNumber(String billNumber);

    List<Bill> findByStatus(BillStatus status);

    long countByStatus(BillStatus status);

    List<Bill> findTop5ByShopkeeperPhoneAndStatusOrderByConfirmedAtDesc(
        String phone, BillStatus status);

    List<Bill> findTop10ByStatusOrderByConfirmedAtDesc(BillStatus status);

    // For analytics dashboard
    @Query("SELECT SUM(b.grandTotal) FROM Bill b WHERE b.status = 'CONFIRMED' " +
           "AND b.confirmedAt BETWEEN :from AND :to")
    Optional<BigDecimal> sumRevenueByDateRange(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.status = 'CONFIRMED' " +
           "AND b.confirmedAt BETWEEN :from AND :to")
    long countConfirmedBillsByDateRange(LocalDateTime from, LocalDateTime to);
}
