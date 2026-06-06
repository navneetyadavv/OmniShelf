package com.omnishelf.engine.service;

import com.omnishelf.engine.model.Customer;
import com.omnishelf.engine.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepo;

    /**
     * Find an existing customer by name (case-insensitive) or create a new one.
     * Returns null if customerName is blank or null.
     */
    @Transactional
    public Customer upsert(String customerName) {
        if (customerName == null || customerName.isBlank()) return null;

        String trimmed = customerName.trim();
        Optional<Customer> existing = customerRepo.findByNameIgnoreCase(trimmed);
        if (existing.isPresent()) {
            Customer c = existing.get();
            c.setTotalBills(c.getTotalBills() + 1);
            c.setLastPurchaseAt(LocalDateTime.now());
            return customerRepo.save(c);
        }

        Customer c = new Customer();
        c.setName(trimmed);
        c.setTotalBills(1);
        c.setLastPurchaseAt(LocalDateTime.now());
        log.info("New customer record created: '{}'", trimmed);
        return customerRepo.save(c);
    }
}
