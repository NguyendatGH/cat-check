package com.catcheck.shop.api;

import com.catcheck.shop.application.PaymentWebhookService;
import com.catcheck.shop.domain.InvalidWebhookException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/payos")
public class PayOsWebhookController {
    private static final Logger log = LoggerFactory.getLogger(PayOsWebhookController.class);

    private final PaymentWebhookService service;
    public PayOsWebhookController(PaymentWebhookService service) { this.service = service; }

    @PostMapping("/webhook")
    @Operation(operationId = "postPayOsWebhook",
            summary = "Public, no session, CSRF-exempt: trusted only through the PayOS signature. 400 on a bad signature, 200 otherwise.")
    public ResponseEntity<Map<String, Boolean>> webhook(@RequestBody String rawBody) {
        try {
            service.handle(rawBody);
        } catch (InvalidWebhookException e) {
            log.warn("{}", e.getMessage()); // message is a class name only, never body content
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
