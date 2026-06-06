package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillItemRepository extends JpaRepository<BillItem, String> {
}
