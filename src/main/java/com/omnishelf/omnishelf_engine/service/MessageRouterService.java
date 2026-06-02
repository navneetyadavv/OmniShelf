package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MessageRouterService {

    private final TwilioMessagingService twilioMessaging;
    private final NlpOrchestrationService nlpOrchestrationService;

    public MessageRouterService(TwilioMessagingService twilioMessaging,
                                NlpOrchestrationService nlpOrchestrationService) {
        this.twilioMessaging = twilioMessaging;
        this.nlpOrchestrationService = nlpOrchestrationService;
    }

// In MessageRouterService.java — update the route() method
public void route(String phone, String text) {
    String lower = text.toLowerCase().trim();

    if (lower.matches("(hi|hello|start|hey).*")) {
        handleGreeting(phone);
    } else if (lower.equals("help")) {
        handleHelp(phone);
    } else if (lower.equals("done") || lower.equals("confirm")) {
        // Phase 3 will handle this fully
        twilioMessaging.send(phone, "Invoice generation coming in Phase 3!");
    } else {
        // Everything else → NLP pipeline
        nlpOrchestrationService.processOrderMessage(phone, text);
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
