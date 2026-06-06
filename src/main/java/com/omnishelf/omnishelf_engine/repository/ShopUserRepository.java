package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.ShopUser;
import com.omnishelf.engine.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ShopUserRepository extends JpaRepository<ShopUser, String> {

    Optional<ShopUser> findByPhone(String phone);

    List<ShopUser> findByRole(UserRole role);

    @Modifying
    @Query("UPDATE ShopUser u SET u.billsGeneratedToday = 0, u.verifiedToday = false")
    void resetDailyCounters();
}
