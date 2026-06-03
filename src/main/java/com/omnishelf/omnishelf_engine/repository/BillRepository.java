package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, String> {
    Optional<Bill> findById(String id);
    List<Bill> findByStatus(com.omnishelf.omnishelf_engine.model.BillStatus status);
    long countByStatus(com.omnishelf.omnishelf_engine.model.BillStatus status);
}