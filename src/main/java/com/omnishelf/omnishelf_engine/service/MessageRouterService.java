package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class MessageRouterService {

    private final TwilioMessagingService twilioMessaging;
    private final NlpOrchestrationService nlpOrchestration;
    private final SessionManagerService sessionManager;


    public MessageRouterService(TwilioMessagingService twilioMessaging,
                             NlpOrchestrationService nlpOrchestration,
                             SessionManagerService sessionManager) {
    this.twilioMessaging  = twilioMessaging;
    this.nlpOrchestration = nlpOrchestration;
    this.sessionManager   = sessionManager;
}

// In MessageRouterService.java — update the route() method
public void route(String phone, String text) {
    String lower = text.toLowerCase().trim();

    if (isGreeting(lower)) {
        handleGreeting(phone);

    } else if (lower.equals("help") || lower.equals("?")) {
        handleHelp(phone);

    } else if (lower.equals("done") || lower.equals("confirm")
            || lower.equals("ho gaya") || lower.equals("bas")) {
        sessionManager.requestConfirmation(phone);   // ← was stub

    } else if (lower.equals("yes") || lower.equals("haan")
            || lower.equals("ha") || lower.equals("ok")) {
        sessionManager.handleYesReply(phone);        // ← new

    } else if (lower.equals("no") || lower.equals("nahi")
            || lower.equals("nope")) {
        sessionManager.handleNoReply(phone);         // ← new

    } else if (lower.equals("undo") || lower.equals("hatao")
            || lower.equals("remove last")) {
        sessionManager.undoLastItem(phone);          // ← was stub

    } else if (lower.equals("cancel") || lower.equals("start over")
            || lower.equals("reset")) {
        sessionManager.cancelSession(phone);         // ← was stub

    } else {
        nlpOrchestration.processOrderMessage(phone, text);
    }
}

    private boolean isGreeting(String lower) {
        return lower.contains("hello") || lower.contains("hi") || lower.contains("hey")
            || lower.contains("namaste") || lower.contains("hi there") || lower.contains("hello there");
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
