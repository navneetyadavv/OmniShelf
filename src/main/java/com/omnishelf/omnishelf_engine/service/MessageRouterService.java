package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MessageRouterService {

    private final TwilioMessagingService twilioMessaging;

    public MessageRouterService(TwilioMessagingService twilioMessaging) {
        this.twilioMessaging = twilioMessaging;
    }

    public void route(String phone, String text) {
        String lower = text.toLowerCase();

        // Primitive intent detection — Gemini replaces this in Phase 2
        if (lower.contains("hi") || lower.contains("hello") || lower.contains("start")) {
            handleGreeting(phone);

        } else if (lower.contains("help")) {
            handleHelp(phone);

        } else {
            // Default: echo back so we confirm the pipeline works end-to-end
            twilioMessaging.send(phone,
                "Got your message: \"" + text + "\"\n" +
                "NLP parsing coming in Phase 2!");
        }
    }

    private void handleGreeting(String phone) {
        twilioMessaging.send(phone,
            "Welcome to the Billing System!\n\n" +
            "Commands coming soon:\n" +
            "• Add items to a bill\n" +
            "• Confirm & generate invoice\n" +
            "• Check stock\n\n" +
            "Type *help* anytime."
        );
    }

    private void handleHelp(String phone) {
        twilioMessaging.send(phone,
            "How to use this system:\n\n" +
            "1. Tell me what you sold (e.g. '2 Nike size 8')\n" +
            "2. I'll confirm the items\n" +
            "3. Say *done* to generate the invoice\n" +
            "4. Invoice PDF sent instantly!"
        );
    }
}
