package com.catcheck.shop.application;

import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class PaymentWebhookService {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway gateway;
    private final ShopRepository repository;
    private final Clock clock;

    public PaymentWebhookService(PaymentGateway gateway, ShopRepository repository, Clock clock) {
        this.gateway = gateway;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Verifies the signature, then records the payment (idempotent in the repository).
     * Returns null when PayOS reports an unsuccessful payment. Never logs the body: it carries payer PII.
     */
    public MarkPaidResult handle(String rawBody) {
        VerifiedPayment payment;
        try {
            payment = gateway.verifyWebhook(rawBody);
        } catch (IllegalStateException e) { // gateway disabled: no keys, so nothing can be trusted
            throw new InvalidWebhookException("PayOS is not configured");
        }
        if (!payment.success()) return null;
        MarkPaidResult result = repository.markPaid(payment.payosOrderCode(), payment.amountVnd(), payment.reference(), clock.instant());
        switch (result) {
            case PAID -> log.info("PayOS payment recorded: orderCode={}", payment.payosOrderCode());
            // PayOS re-sends a transfer with the same reference; another reference is a second transfer to refund by hand.
            case ALREADY_PAID -> repository.paymentReference(payment.payosOrderCode())
                    .filter(stored -> !stored.equals(payment.reference()))
                    .ifPresent(stored -> log.warn("PayOS webhook ALREADY_PAID with a different reference, check for a refund: orderCode={}",
                            payment.payosOrderCode()));
            default -> log.warn("PayOS webhook {}: orderCode={}", result, payment.payosOrderCode());
        }
        return result;
    }
}
