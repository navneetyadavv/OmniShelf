package com.billing.repository;

import com.billing.model.ShopUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShopUserRepository extends JpaRepository<ShopUser, String> {
    Optional<ShopUser> findByPhone(String phone);

    // Reset daily counters at midnight
    @Modifying
    @Query("UPDATE ShopUser u SET u.billsGeneratedToday = 0, u.verifiedToday = false")
    void resetDailyCounters();
}