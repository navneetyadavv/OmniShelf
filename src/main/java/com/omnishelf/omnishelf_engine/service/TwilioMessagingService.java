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
        try {
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + toPhone),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();

            log.info("Message sent. SID: {}", message.getSid());

        } catch (ApiException e) {
            log.error("Twilio send failed for {}: {}", toPhone, e.getMessage());
        }
    }
}
