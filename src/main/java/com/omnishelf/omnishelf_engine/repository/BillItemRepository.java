package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillItemRepository extends JpaRepository<BillItem, String> {
}
