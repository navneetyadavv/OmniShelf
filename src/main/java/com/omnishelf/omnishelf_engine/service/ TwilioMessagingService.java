// TwilioMessagingService.java
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