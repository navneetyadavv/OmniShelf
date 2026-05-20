// WhatsAppWebhookController.java
@RestController
@RequestMapping("/webhook")
@Slf4j
public class WhatsAppWebhookController {

    private final MessageRouterService messageRouter;

    public WhatsAppWebhookController(MessageRouterService messageRouter) {
        this.messageRouter = messageRouter;
    }

    @PostMapping(value = "/whatsapp",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> receiveMessage(
            @RequestParam("From") String from,
            @RequestParam("Body") String body,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl) {

        log.info("Incoming message from: {} | body: {}", from, body);

        // Sanitize phone number: "whatsapp:+919876543210" → "+919876543210"
        String phone = from.replace("whatsapp:", "").trim();
        String text  = body.trim();

        // Hand off to the router — non-blocking, return 200 OK immediately
        messageRouter.route(phone, text);

        // Twilio expects a 200 OK with TwiML or empty body
        return ResponseEntity.ok("<Response></Response>");
    }
}