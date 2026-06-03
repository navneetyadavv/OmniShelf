package com.omnishelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WhatsAppBillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsAppBillingApplication.class, args);
    }
} 