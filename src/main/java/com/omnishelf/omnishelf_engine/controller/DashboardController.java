package com.omnishelf.engine.controller;

import com.omnishelf.engine.model.BillStatus;
import com.omnishelf.engine.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final BillRepository           billRepo;
    private final ProductVariantRepository variantRepo;
    private final ShopUserRepository       userRepo;

    @GetMapping
    public String dashboard(Model model) {
        LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime dayEnd   = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        BigDecimal todayRevenue = billRepo.sumRevenueByDateRange(dayStart, dayEnd)
            .orElse(BigDecimal.ZERO);
        long todayBills = billRepo.countConfirmedBillsByDateRange(dayStart, dayEnd);

        model.addAttribute("totalBills",      billRepo.countByStatus(BillStatus.CONFIRMED));
        model.addAttribute("todayRevenue",    todayRevenue);
        model.addAttribute("todayBills",      todayBills);
        model.addAttribute("recentBills",
            billRepo.findTop10ByStatusOrderByConfirmedAtDesc(BillStatus.CONFIRMED));
        model.addAttribute("lowStockItems",   variantRepo.findLowStockVariants(5));
        model.addAttribute("totalUsers",      userRepo.count());

        return "dashboard";
    }
}
