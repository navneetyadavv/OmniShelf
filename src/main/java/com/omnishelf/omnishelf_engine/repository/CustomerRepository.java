package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByNameIgnoreCase(String name);

    Optional<Customer> findByPhone(String phone);
}
