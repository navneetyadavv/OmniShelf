package com.omnishelf.omnishelf_engine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
@Slf4j
public class TwilioMessagingService {

    @Value("${twilio.whatsapp-number}")
    private String fromNumber;

    public void send(String toPhone, String messageBody) {
        if (fromNumber == null || fromNumber.isBlank()) {
            log.error("Twilio WhatsApp sender number is not configured");
            return;
        }

        String normalizedFrom = fromNumber.startsWith("whatsapp:") ? fromNumber : "whatsapp:" + fromNumber;
        String normalizedTo = toPhone.startsWith("whatsapp:") ? toPhone : "whatsapp:" + toPhone;

        try {
            Message message = Message.creator(
                    new PhoneNumber(normalizedTo),
                    new PhoneNumber(normalizedFrom),
                    messageBody
            ).create();

            log.info("Message sent. SID: {}", message.getSid());

        } catch (Exception e) {
            log.error("Twilio send failed for {}: {}", toPhone, e.getMessage(), e);
        }
    }
}
