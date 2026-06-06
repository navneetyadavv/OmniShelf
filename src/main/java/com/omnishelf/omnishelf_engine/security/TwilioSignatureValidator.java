package com.omnishelf.engine.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates the X-Twilio-Signature header on incoming webhook requests.
 * Without this, anyone who discovers the /webhook/whatsapp endpoint can
 * send fake billing messages.
 *
 * Algorithm (Twilio docs):
 * 1. Take the full URL of the request
 * 2. Sort all POST parameters alphabetically and append key+value pairs
 * 3. Sign the result with your Auth Token using HMAC-SHA1
 * 4. Base64-encode the result and compare to the header
 */
@Component
@Slf4j
public class TwilioSignatureValidator {

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.webhook-validation-enabled:true}")
    private boolean validationEnabled;

    /**
     * Returns true if the request signature is valid or validation is disabled.
     */
    public boolean isValid(String twilioSignature, String requestUrl,
                           Map<String, String> params) {
        if (!validationEnabled) {
            log.debug("Twilio signature validation disabled — skipping check");
            return true;
        }

        if (twilioSignature == null || twilioSignature.isBlank()) {
            log.warn("Missing X-Twilio-Signature header on webhook request");
            return false;
        }

        try {
            // Build the string to sign: URL + sorted params
            StringBuilder sb = new StringBuilder(requestUrl);
            new TreeMap<>(params).forEach((k, v) -> sb.append(k).append(v));

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hash);

            boolean valid = expected.equals(twilioSignature);
            if (!valid) {
                log.warn("Twilio signature mismatch — possible webhook forgery attempt");
            }
            return valid;

        } catch (Exception e) {
            log.error("Error validating Twilio signature: {}", e.getMessage());
            return false;
        }
    }
}
