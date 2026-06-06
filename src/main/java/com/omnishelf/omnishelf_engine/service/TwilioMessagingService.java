package com.omnishelf.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
@Slf4j
public class TwilioMessagingService {

    @Value("${twilio.whatsapp-number}")
    private String fromNumber;

    private static final int MAX_RETRIES = 3;

    public void send(String toPhone, String messageBody) {
        if (fromNumber == null || fromNumber.isBlank()) {
            log.error("Twilio sender number not configured");
            return;
        }

        String normalizedFrom = fromNumber.startsWith("whatsapp:") ? fromNumber : "whatsapp:" + fromNumber;
        String normalizedTo   = toPhone.startsWith("whatsapp:")   ? toPhone   : "whatsapp:" + toPhone;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Message message = Message.creator(
                    new PhoneNumber(normalizedTo),
                    new PhoneNumber(normalizedFrom),
                    messageBody
                ).create();
                log.info("Message sent to {} | SID: {}", toPhone, message.getSid());
                return;
            } catch (Exception e) {
                log.warn("Twilio send attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, toPhone, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("All {} Twilio send attempts exhausted for {}", MAX_RETRIES, toPhone);
                }
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }
}
