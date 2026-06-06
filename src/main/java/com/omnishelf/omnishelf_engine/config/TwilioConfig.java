package com.omnishelf.engine.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TwilioConfig {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @PostConstruct
    public void initTwilio() {
        if (accountSid == null || accountSid.startsWith("AC") && accountSid.length() < 10) {
            log.warn("Twilio credentials appear to be stubs — messaging will be disabled");
            return;
        }
        Twilio.init(accountSid, authToken);
        log.info("Twilio initialized successfully");
    }
}
