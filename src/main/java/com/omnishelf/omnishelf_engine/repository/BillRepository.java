package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.Bill;
import com.omnishelf.omnishelf_engine.model.BillStatus; // Added explicit import
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, String> {
    
    // Existing Methods
    Optional<Bill> findById(String id);
    
    List<Bill> findByStatus(BillStatus status);
    
    long countByStatus(BillStatus status);

    // New Feature Methods
    Optional<Bill> findByBillNumber(String billNumber);

    List<Bill> findTop5ByShopkeeperPhoneAndStatusOrderByConfirmedAtDesc(
        String phone, 
        BillStatus status
    );
}